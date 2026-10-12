package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.personlocation

import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.atMost
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.reset
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyBlocking
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.returnResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.MigrationResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.personlocation.PersonLocationDpsApiExtension.Companion.dpsPersonLocationServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.personlocation.PersonLocationDpsApiMockServer.Companion.resyncResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonLocationMovementMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonLocationsMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonNumberAndRootOffenderId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistoryRepository
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personlocation.model.ResyncExternalMovementsRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personlocation.model.SyncCustodialSeries
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personlocation.model.SyncExternalJourney
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personlocation.model.SyncExternalMovement
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personlocation.model.SyncExternalMovementFrom
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personlocation.model.SyncExternalMovementReason
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PersonLocationMigrationIntTest(
  @Autowired private val migrationHistoryRepository: MigrationHistoryRepository,
  @Autowired private val personLocationNomisApi: PersonLocationNomisApiMockServer,
  @Autowired private val mappingApi: PersonLocationMappingApiMockServer,
) : PersonLocationIntegrationTestBase() {

  private val nomisApi = NomisApiExtension.nomisApi
  private val dpsApi = dpsPersonLocationServer

  private val dpsCustodialSeriesId = UUID.randomUUID()
  private val dpsMovementId1 = UUID.randomUUID()
  private val dpsMovementId2 = UUID.randomUUID()
  private lateinit var migrationId: String

  // Because of how the ID ranges stubs work, we start with the unintuitive rootOffenderId=0 and prisonerNumber="A0000KT"
  private val prisonerNumber = "A0000KT"

  override fun resetTelemetryClient() {}

  internal fun setupMigrationTest() = runBlocking {
    migrationHistoryRepository.deleteAll()

    NomisApiExtension.resetAndDisableResetBeforeEach()
    MappingApiExtension.resetAndDisableResetBeforeEach()
    PersonLocationDpsApiExtension.resetAndDisableResetBeforeEach()

    tearDownTelemetryClient()
    reset(personLocationMigrationService)
  }

  @AfterAll
  fun tearDownTelemetryClient() = reset(telemetryClient)

  private fun stubMigrationDependencies(entities: Int, pageSize: Long) {
    nomisApi.stubGetPrisonerIds(entities.toLong(), 1, prisonerNumber)
    nomisApi.stubGetAllPrisonersIdRangesAndInRange(pageSize = pageSize, totalElements = entities.toLong())
    ((0 until entities).map { index -> "A%04dKT".format(index) } + "A1234BC")
      .forEach { prisonerNumber ->
        personLocationNomisApi.stubGetOffenderPersonLocations(prisonerNumber)
        dpsApi.stubResyncPrisoner(
          personIdentifier = prisonerNumber,
          response = resyncResponse(
            dpsCustodialSeriesId = dpsCustodialSeriesId,
            movements = listOf(1 to dpsMovementId1, 2 to dpsMovementId2),
          ),
        )
      }
    mappingApi.stubCreatePersonLocationMappings()
  }

  private fun migratedPrisonerNumbers(count: Int): List<String> = argumentCaptor<MigrationContext<PrisonNumberAndRootOffenderId>>().run {
    verifyBlocking(personLocationMigrationService, times(count)) { migrateNomisEntity(capture()) }
    allValues.map { it.body.prisonNumber }
  }

  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  inner class MigrateEntity {
    @BeforeAll
    fun setUp() {
      setupMigrationTest()

      stubMigrationDependencies(entities = 1, pageSize = 1)
      migrationId = performMigration()
    }

    @Test
    fun `should get NOMIS person locations`() {
      personLocationNomisApi.verify(getRequestedFor(urlPathEqualTo("/movements/$prisonerNumber/person-locations")))
    }

    @Test
    fun `should populate DPS custodial series`() {
      PersonLocationDpsApiMockServer.getRequestBody<ResyncExternalMovementsRequest>(
        putRequestedFor(urlPathEqualTo("/resync/external-movements/$prisonerNumber")),
      ).apply {
        assertThat(custodialSeries).hasSize(1)
        with(custodialSeries[0]) {
          assertThat(created.at).isEqualTo(LocalDateTime.parse("2024-01-01T10:00:00"))
          assertThat(created.by).isEqualTo("SYS")
          assertThat(modified).isNull()
        }
        with(custodialSeries[0].custodialSeries) {
          assertThat(dpsId).isNull()
          assertThat(legacyBookingId).isEqualTo(12345L)
          assertThat(legacyBookingReference).isEqualTo("B12345")
          assertThat(status).isEqualTo(SyncCustodialSeries.Status.OPEN)
          assertThat(isActive).isTrue
          assertThat(openedAt).isEqualTo(LocalDateTime.parse("2024-01-01T10:00:00"))
          assertThat(closedAt).isNull()
          assertThat(notes).isNull()
        }
      }
    }

    @Test
    fun `should populate DPS movement with locations`() {
      PersonLocationDpsApiMockServer.getRequestBody<ResyncExternalMovementsRequest>(
        putRequestedFor(urlPathEqualTo("/resync/external-movements/$prisonerNumber")),
      ).apply {
        with(custodialSeries[0].movements[0]) {
          assertThat(created.at).isEqualTo(LocalDateTime.parse("2024-01-01T10:00:00"))
          assertThat(created.by).isEqualTo("SOME_USER")
          assertThat(modified!!.at).isEqualTo(LocalDateTime.parse("2024-01-01T11:00:00"))
          assertThat(modified.by).isEqualTo("SYS")
        }
        with(custodialSeries[0].movements[0].movement) {
          assertThat(dpsId).isNull()
          assertThat(dpsCustodySeriesId).isNull()
          assertThat(legacyBookingId).isEqualTo(12345L)
          assertThat(legacySequenceNumber).isEqualTo(1)
          assertThat(type).isEqualTo(SyncExternalMovement.Type.ARRIVAL)
          assertThat(occurredAt).isEqualTo(LocalDateTime.parse("2024-01-01T10:00:00"))
          assertThat(reason).isEqualTo(SyncExternalMovementReason(description = "Unconvicted Remand", reasonType = "CodedReason", code = "N", hierarchy = listOf()))
          assertThat(from).isEqualTo(SyncExternalMovementFrom(description = "Leeds Court", locationType = "Court", code = "LEEDCC"))
          assertThat(to).isEqualTo(SyncExternalMovementFrom(description = "Leeds (HMP)", locationType = "Prison", code = "LEI"))
          assertThat(journey).isEqualTo(SyncExternalJourney(type = SyncExternalJourney.Type.ADMISSION, scheduleReference = null))
          assertThat(notes).isNull()
        }
      }
    }

    @Test
    fun `should populate DPS movement with missing and simple locations`() {
      PersonLocationDpsApiMockServer.getRequestBody<ResyncExternalMovementsRequest>(
        putRequestedFor(urlPathEqualTo("/resync/external-movements/$prisonerNumber")),
      ).apply {
        with(custodialSeries[0].movements[1].movement) {
          assertThat(legacySequenceNumber).isEqualTo(2)
          assertThat(type).isEqualTo(SyncExternalMovement.Type.DEPARTURE)
          assertThat(occurredAt).isEqualTo(LocalDateTime.parse("2024-01-11T10:00:00"))
          assertThat(reason).isEqualTo(SyncExternalMovementReason(description = "Normal Transfer", reasonType = "CodedReason", code = "NOTR", hierarchy = listOf()))
          assertThat(from).isEqualTo(SyncExternalMovementFrom(description = "", locationType = "SimpleLocation", code = ""))
          assertThat(to).isEqualTo(SyncExternalMovementFrom(description = "Unknown destination", locationType = "SimpleLocation", code = ""))
          assertThat(journey).isEqualTo(SyncExternalJourney(type = SyncExternalJourney.Type.TRANSFER, scheduleReference = "54321"))
          assertThat(notes).isEqualTo("Transfer comment")
        }
      }
    }

    @Test
    fun `should create mappings`() {
      PersonLocationMappingApiMockServer.getRequestBody<PersonLocationsMappingDto>(
        putRequestedFor(urlPathEqualTo("/mapping/person-location/migrate")),
      ).apply {
        assertThat(offenderNo).isEqualTo(prisonerNumber)
        assertThat(migrationId).isEqualTo(this@PersonLocationMigrationIntTest.migrationId)
        assertThat(bookings).hasSize(1)
        with(bookings[0]) {
          assertThat(bookingId).isEqualTo(12345L)
          assertThat(dpsCustodialSeriesId).isEqualTo(this@PersonLocationMigrationIntTest.dpsCustodialSeriesId)
          assertThat(movements).containsExactly(
            PersonLocationMovementMappingDto(nomisMovementSeq = 1, dpsExternalMovementId = dpsMovementId1),
            PersonLocationMovementMappingDto(nomisMovementSeq = 2, dpsExternalMovementId = dpsMovementId2),
          )
        }
      }
    }

    @Test
    fun `will publish telemetry`() {
      verify(telemetryClient).trackEvent(
        eq("person-location-migration-entity-migrated"),
        check {
          assertThat(it["offenderNo"]).isEqualTo(prisonerNumber)
          assertThat(it["migrationId"]).isEqualTo(migrationId)
        },
        isNull(),
      )
    }
  }

  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  inner class FullMigration {
    @BeforeAll
    fun setUp() {
      setupMigrationTest()

      stubMigrationDependencies(entities = 3, pageSize = 2)
      performMigration()
    }

    @Test
    fun `should migrate all prisoners`() {
      assertThat(migratedPrisonerNumbers(3)).containsExactlyInAnyOrder("A0000KT", "A0001KT", "A0002KT")
    }
  }

  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  inner class MigrateSinglePrisoner {
    @BeforeAll
    fun setUp() {
      setupMigrationTest()

      stubMigrationDependencies(entities = 100, pageSize = 10)
      performMigration("A1234BC")
    }

    @Test
    fun `should migrate only the requested prisoner`() {
      assertThat(migratedPrisonerNumbers(1)).containsExactly("A1234BC")
    }
  }

  @Nested
  inner class Security {
    @Test
    fun `access forbidden when no role`() {
      webTestClient.post().uri("/migrate/person-location")
        .headers(setAuthorisation(roles = listOf()))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(PersonLocationMigrationFilter())
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `access forbidden with wrong role`() {
      webTestClient.post().uri("/migrate/person-location")
        .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(PersonLocationMigrationFilter())
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `access unauthorised with no auth token`() {
      webTestClient.post().uri("/migrate/person-location")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(PersonLocationMigrationFilter())
        .exchange()
        .expectStatus().isUnauthorized
    }
  }

  private fun performMigration(prisonerNumber: String? = null): String = webTestClient.post()
    .uri("/migrate/person-location")
    .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_FROM_NOMIS__MIGRATION__RW")))
    .contentType(MediaType.APPLICATION_JSON)
    .bodyValue(PersonLocationMigrationFilter(prisonerNumber = prisonerNumber))
    .exchange()
    .expectStatus().isAccepted
    .returnResult<MigrationResult>().responseBody.blockFirst()!!
    .migrationId
    .also {
      waitUntilCompleted()
    }

  private fun waitUntilCompleted() = await atMost Duration.ofSeconds(60) untilAsserted {
    verify(telemetryClient).trackEvent(
      eq("person-location-migration-completed"),
      any(),
      isNull(),
    )
  }
}
