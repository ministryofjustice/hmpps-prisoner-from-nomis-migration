package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.court

import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.awaitility.kotlin.atMost
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.returnResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtscheduler.model.ResyncCourtEvents
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.CourtSentencingMappingApiMockServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.MigrationResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.MigrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.court.CourtSchedulerDpsApiExtension.Companion.dpsCourtSchedulerServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.court.CourtSchedulerDpsApiMockServer.Companion.resyncResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtAppearanceMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtSchedulerPrisonerMappingIdsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtSchedulerPrisonerMappingsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderCourtMovementsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CourtSchedulerMigrationIntTest(
  @Autowired private val nomisApi: CourtSchedulerNomisApiMockServer,
  @Autowired private val mappingApi: CourtSchedulerMappingApiMockServer,
  @Autowired private val sentencingMappingApi: CourtSentencingMappingApiMockServer,
) : MigrationTestBase() {

  private val dpsApi = dpsCourtSchedulerServer

  private val dpsCourtScheduleId = UUID.randomUUID()
  private val dpsScheduledMovementOutId = UUID.randomUUID()
  private val dpsScheduledMovementInId = UUID.randomUUID()
  private val dpsUnscheduledMovementOutId = UUID.randomUUID()
  private val dpsUnscheduledMovementInId = UUID.randomUUID()
  private val dpsSentencingCourtAppearanceId = UUID.randomUUID()
  private lateinit var migrationId: String

  @AfterAll
  fun tearDownTelemetryClient() = reset(telemetryClient)

  private fun stubMigrationDependencies(entities: Int = 2) {
    NomisApiExtension.nomisApi.stubGetPrisonerIds(totalElements = entities.toLong(), pageSize = 10, firstOffenderNo = "A0001KT")
    mappingApi.stubCreateCourtSchedulerPrisonerMappings()
    (1..entities)
      .map { index -> "A%04dKT".format(index) }
      .forEach { prisonerNumber ->
        mappingApi.stubGetCourtSchedulerPrisonerMappingIds(prisonerNumber, 12345L, 1, dpsCourtScheduleId, 3, dpsScheduledMovementOutId, 4, dpsScheduledMovementInId, 1, dpsUnscheduledMovementOutId, 2, dpsUnscheduledMovementInId)
        nomisApi.stubGetOffenderCourtMovements(prisonerNumber)
        sentencingMappingApi.stubGetAllCourtAppearanceByNomisIds(
          mappings = listOf(CourtAppearanceMappingDto(1, dpsSentencingCourtAppearanceId.toString(), "any", CourtAppearanceMappingDto.MappingType.MIGRATED)),
        )
        dpsApi.stubResyncPrisonerCourtAppearances(
          personIdentifier = prisonerNumber,
          response = resyncResponse(dpsCourtScheduleId, dpsScheduledMovementOutId, dpsScheduledMovementInId, dpsUnscheduledMovementOutId, dpsUnscheduledMovementInId),
        )
      }
  }

  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  inner class MigrateEntity {
    @BeforeAll
    fun setUp() = runTest {
      setupMigrationTest()

      stubMigrationDependencies(entities = 1)
      migrationId = performMigration()
    }

    @Test
    fun `should check for existing mappings`() {
      mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/court/A0001KT/ids")))
    }

    @Test
    fun `should get NOMIS court movement details`() {
      nomisApi.verify(getRequestedFor(urlPathEqualTo("/movements/A0001KT/court")))
    }

    @Test
    fun `should get RaS court sentencing mappings`() {
      sentencingMappingApi.verify(
        postRequestedFor(urlPathEqualTo("/mapping/court-sentencing/court-appearances/nomis-court-appearance-ids/get-list")),
      )
    }

    @Test
    fun `should call DPS resync API`() {
      dpsApi.verify(putRequestedFor(urlPathEqualTo("/resync/court-appearances/A0001KT")))
    }

    @Test
    fun `should populate DPS court appearance`() {
      CourtSchedulerDpsApiMockServer.getRequestBody<ResyncCourtEvents>(
        putRequestedFor(urlPathEqualTo("/resync/court-appearances/A0001KT")),
      ).apply {
        with(courtEvents[0].courtEvent) {
          assertThat(dpsId).isEqualTo(dpsCourtScheduleId)
          assertThat(eventId).isEqualTo(1)
          assertThat(prisonCodeAtTimeOfScheduling).isEqualTo("BXI")
          assertThat(eventDate).isEqualTo(LocalDate.now().minusDays(1))
          assertThat(LocalDateTime.parse(startTime)).isCloseTo(LocalDateTime.now().minusDays(1), within(5, ChronoUnit.MINUTES))
          assertThat(courtEventType).isEqualTo("CRT")
          assertThat(eventStatus).isEqualTo("COMP")
          assertThat(commentText).isEqualTo("Some schedule comment")
          assertThat(externalReferenceUrn).isEqualTo("$EXTERNAL_REF_PREFIX$dpsSentencingCourtAppearanceId")
        }
        with(courtEvents[0].created) {
          assertThat(by).isEqualTo("USER")
          assertThat(at).isCloseTo(LocalDateTime.now().minusDays(1), within(5, ChronoUnit.MINUTES))
        }
        assertThat(courtEvents[0].modified).isNull()
      }
    }

    @Test
    fun `should populate DPS court movement`() {
      CourtSchedulerDpsApiMockServer.getRequestBody<ResyncCourtEvents>(
        putRequestedFor(urlPathEqualTo("/resync/court-appearances/A0001KT")),
      ).apply {
        with(courtEvents[0].movements[0].movement) {
          assertThat(dpsId).isEqualTo(dpsScheduledMovementOutId)
          assertThat(offenderBookId).isEqualTo(12345)
          assertThat(movementSeq).isEqualTo(3)
          assertThat(movementDate).isEqualTo(LocalDate.now().minusDays(1))
          assertThat(LocalDateTime.parse(movementTime)).isCloseTo(LocalDateTime.now().minusDays(1), within(5, ChronoUnit.MINUTES))
          assertThat(movementReasonCode).isEqualTo("CRT")
          assertThat(fromAgencyId).isEqualTo("BXI")
          assertThat(toAgencyId).isEqualTo("LEEDMC")
          assertThat(commentText).isEqualTo("Some movement out comment")
        }
        with(courtEvents[0].movements[0].created) {
          assertThat(by).isEqualTo("USER")
          assertThat(at).isCloseTo(LocalDateTime.now().minusDays(1), within(5, ChronoUnit.MINUTES))
        }
        assertThat(courtEvents[0].movements[0].modified).isNull()
      }
    }

    @Test
    fun `should send all movements`() {
      CourtSchedulerDpsApiMockServer.getRequestBody<ResyncCourtEvents>(
        putRequestedFor(urlPathEqualTo("/resync/court-appearances/A0001KT")),
      ).apply {
        assertThat(courtEvents[0].movements.size).isEqualTo(2)
        assertThat(courtEvents[0].movements[0].movement.movementSeq).isEqualTo(3)
        assertThat(courtEvents[0].movements[1].movement.movementSeq).isEqualTo(4)
        assertThat(unscheduledMovements.size).isEqualTo(2)
        assertThat(unscheduledMovements[0].movement.movementSeq).isEqualTo(1)
        assertThat(unscheduledMovements[1].movement.movementSeq).isEqualTo(2)
      }
    }

    @Test
    fun `should update mappings`() {
      CourtSchedulerMappingApiMockServer.getRequestBody<CourtSchedulerPrisonerMappingsDto>(
        putRequestedFor(urlPathEqualTo("/mapping/court/migrate")),
      ).apply {
        assertThat(offenderNo).isEqualTo("A0001KT")
        with(bookings[0]) {
          assertThat(bookingId).isEqualTo(12345)
          with(courtSchedules[0]) {
            assertThat(nomisEventId).isEqualTo(1)
            assertThat(dpsCourtAppearanceId).isEqualTo(dpsCourtScheduleId)
            assertThat(movements[0].nomisMovementSeq).isEqualTo(3)
            assertThat(movements[0].dpsCourtMovementId).isEqualTo(dpsScheduledMovementOutId)
            assertThat(movements[1].nomisMovementSeq).isEqualTo(4)
            assertThat(movements[1].dpsCourtMovementId).isEqualTo(dpsScheduledMovementInId)
          }
          assertThat(unscheduledMovements[0].nomisMovementSeq).isEqualTo(1)
          assertThat(unscheduledMovements[0].dpsCourtMovementId).isEqualTo(dpsUnscheduledMovementOutId)
          assertThat(unscheduledMovements[1].nomisMovementSeq).isEqualTo(2)
          assertThat(unscheduledMovements[1].dpsCourtMovementId).isEqualTo(dpsUnscheduledMovementInId)
        }
      }
    }

    @Test
    fun `will publish telemetry`() {
      verify(telemetryClient).trackEvent(
        eq("court-movements-migration-entity-migrated"),
        check {
          assertThat(it["offenderNo"]).isEqualTo("A0001KT")
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
    fun setUp() = runTest {
      setupMigrationTest()

      stubMigrationDependencies(entities = 2)
      migrationId = performMigration()
    }

    @Test
    fun `should call DPS resync API for both prisoners`() {
      dpsApi.verify(putRequestedFor(urlPathEqualTo("/resync/court-appearances/A0001KT")))
      dpsApi.verify(putRequestedFor(urlPathEqualTo("/resync/court-appearances/A0002KT")))
    }

    @Test
    fun `should update mappings for both prisoners`() {
      mappingApi.verify(
        count = 2,
        putRequestedFor(urlPathEqualTo("/mapping/court/migrate")),
      )
    }

    @Test
    fun `will publish telemetry for both prisoners`() {
      verify(telemetryClient).trackEvent(
        eq("court-movements-migration-entity-migrated"),
        check {
          assertThat(it["offenderNo"]).isEqualTo("A0001KT")
          assertThat(it["migrationId"]).isEqualTo(migrationId)
        },
        isNull(),
      )
      verify(telemetryClient).trackEvent(
        eq("court-movements-migration-entity-migrated"),
        check {
          assertThat(it["offenderNo"]).isEqualTo("A0002KT")
          assertThat(it["migrationId"]).isEqualTo(migrationId)
        },
        isNull(),
      )
    }
  }

  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  inner class MigrationFailure {
    @BeforeAll
    fun setUp() = runTest {
      setupMigrationTest()

      stubMigrationDependencies(entities = 1)
      // The call to DPS fails
      dpsApi.stubResyncPrisonerCourtAppearances("A0001KT", 400)
      migrationId = performMigration()
    }

    @Test
    fun `should call DPS resync API`() {
      dpsApi.verify(putRequestedFor(urlPathEqualTo("/resync/court-appearances/A0001KT")))
    }

    @Test
    fun `should not update mappings`() {
      mappingApi.verify(
        count = 0,
        putRequestedFor(urlPathEqualTo("/mapping/court/migrate")),
      )
    }

    @Test
    fun `will publish failure telemetry`() {
      verify(telemetryClient).trackEvent(
        eq("court-movements-migration-entity-failed"),
        check {
          assertThat(it["offenderNo"]).isEqualTo("A0001KT")
          assertThat(it["migrationId"]).isEqualTo(migrationId)
          assertThat(it["reason"]).isEqualTo("400 Bad Request from PUT http://localhost:8106/resync/court-appearances/A0001KT")
        },
        isNull(),
      )
    }
  }

  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  inner class MappingErrorRecovery {
    @BeforeAll
    fun setUp() = runTest {
      setupMigrationTest()

      stubMigrationDependencies(entities = 1)
      // The call to update mappings fails then succeeds on a retry
      mappingApi.stubCreateCourtSchedulePrisonerMappingsFailureFollowedBySuccess()
      migrationId = performMigration()
    }

    @Test
    fun `should call DPS API once`() {
      dpsApi.verify(
        1,
        putRequestedFor(urlPathEqualTo("/resync/court-appearances/A0001KT")),
      )
    }

    @Test
    fun `should attempt to update mappings twice`() {
      mappingApi.verify(
        count = 2,
        putRequestedFor(urlPathEqualTo("/mapping/court/migrate")),
      )
    }

    @Test
    fun `will publish telemetry`() {
      verify(telemetryClient).trackEvent(
        eq("court-movements-migration-entity-migrated"),
        check {
          assertThat(it["offenderNo"]).isEqualTo("A0001KT")
          assertThat(it["migrationId"]).isEqualTo(migrationId)
        },
        isNull(),
      )
    }
  }

  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  inner class IgnoreOffendersWithNoMovements {
    @BeforeAll
    fun setUp() = runTest {
      setupMigrationTest()

      stubMigrationDependencies(entities = 1)
      // There are no court movements for this prisoner
      mappingApi.stubGetCourtSchedulerPrisonerMappingIds(
        prisonerNumber = "A0001KT",
        idMappings = CourtSchedulerPrisonerMappingIdsDto("A0001KT", listOf(), listOf()),
      )
      nomisApi.stubGetOffenderCourtMovements(
        offenderNo = "A0001KT",
        response = OffenderCourtMovementsResponse(listOf()),
      )

      migrationId = performMigration()
    }

    @Test
    fun `should NOT migrate prisoner to DPS`() {
      dpsApi.verify(
        0,
        putRequestedFor(urlPathEqualTo("/resync/court-appearances/A0001KT")),
      )
    }

    @Test
    fun `should NOT update mappings`() {
      mappingApi.verify(
        count = 0,
        putRequestedFor(urlPathEqualTo("/mapping/court/migrate")),
      )
    }

    @Test
    fun `should publish ignore telemetry`() {
      verify(telemetryClient).trackEvent(
        eq("court-movements-migration-entity-ignored"),
        check {
          assertThat(it["offenderNo"]).isEqualTo("A0001KT")
          assertThat(it["migrationId"]).isEqualTo(migrationId)
          assertThat(it["reason"]).isEqualTo("The offender has no court movements")
        },
        isNull(),
      )
    }
  }

  @Nested
  inner class RepairEndpoint {
    private val prisonerNumber = "A0001KT"

    @BeforeEach
    fun setUp() = runTest {
      stubMigrationDependencies(entities = 1)
      reset(telemetryClient)
    }

    @Nested
    inner class HappyPath {
      @BeforeEach
      fun setUp() = runTest {
        repairPrisonerOk(prisonerNumber)
      }

      @Test
      fun `should check for existing mappings`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/court/A0001KT/ids")))
      }

      @Test
      fun `should get NOMIS court movement details`() {
        nomisApi.verify(getRequestedFor(urlPathEqualTo("/movements/A0001KT/court")))
      }

      @Test
      fun `should get RaS court sentencing mappings`() {
        sentencingMappingApi.verify(
          postRequestedFor(urlPathEqualTo("/mapping/court-sentencing/court-appearances/nomis-court-appearance-ids/get-list")),
        )
      }

      @Test
      fun `should call DPS resync API`() {
        dpsApi.verify(putRequestedFor(urlPathEqualTo("/resync/court-appearances/A0001KT")))
      }

      @Test
      fun `should update mappings`() {
        mappingApi.verify(
          putRequestedFor(urlPathEqualTo("/mapping/court/migrate"))
            .withRequestBodyJsonPath("offenderNo", "A0001KT"),
        )
      }

      @Test
      fun `will publish telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("court-movements-migration-entity-repair-requested"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A0001KT")
          },
          isNull(),
        )
        verify(telemetryClient).trackEvent(
          eq("court-movements-migration-entity-migrated"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A0001KT")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class DontIgnoreOffendersWithNoMovements {
      @BeforeEach
      fun setUp() = runTest {
        stubMigrationDependencies(entities = 1)

        // There are no court movements for this prisoner
        mappingApi.stubGetCourtSchedulerPrisonerMappingIds(
          prisonerNumber = "A0001KT",
          idMappings = CourtSchedulerPrisonerMappingIdsDto("A0001KT", listOf(), listOf()),
        )
        nomisApi.stubGetOffenderCourtMovements(
          offenderNo = "A0001KT",
          response = OffenderCourtMovementsResponse(listOf()),
        )

        repairPrisonerOk(prisonerNumber)
      }

      @Test
      fun `will migrate to DPS`() {
        dpsApi.verify(putRequestedFor(urlEqualTo("/resync/court-appearances/A0001KT")))
      }

      @Test
      fun `will update mappings`() {
        mappingApi.verify(
          putRequestedFor(urlEqualTo("/mapping/court/migrate"))
            .withRequestBodyJsonPath("offenderNo", "A0001KT")
            .withRequestBodyJsonPath("bookings.length()", 0),
        )
      }

      @Test
      fun `will publish telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("court-movements-migration-entity-repair-requested"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A0001KT")
          },
          isNull(),
        )
        verify(telemetryClient).trackEvent(
          eq("court-movements-migration-entity-migrated"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A0001KT")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class DontIgnoreOffendersNotInNomis {
      @BeforeEach
      fun setUp() = runTest {
        stubMigrationDependencies(entities = 1)

        nomisApi.stubGetOffenderCourtMovements(status = NOT_FOUND)
        dpsApi.stubResyncPrisonerCourtAppearances(
          personIdentifier = prisonerNumber,
          status = 404,
        )

        repairPrisonerOk(prisonerNumber)
      }

      @Test
      fun `will migrate to DPS`() {
        dpsApi.verify(putRequestedFor(urlEqualTo("/resync/court-appearances/A0001KT")))
      }

      @Test
      fun `will update mappings`() {
        mappingApi.verify(
          putRequestedFor(urlEqualTo("/mapping/court/migrate"))
            .withRequestBodyJsonPath("offenderNo", "A0001KT")
            .withRequestBodyJsonPath("bookings.length()", 0),
        )
      }

      @Test
      fun `will publish telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("court-movements-migration-entity-repair-requested"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A0001KT")
          },
          isNull(),
        )
        verify(telemetryClient).trackEvent(
          eq("court-movements-migration-entity-migrated"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A0001KT")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class Security {

      @Test
      fun `access forbidden when no role`() {
        webTestClient.put().uri("/migrate/court/repair/$prisonerNumber")
          .headers(setAuthorisation(roles = listOf()))
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(CourtSchedulerMigrationFilter())
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.put().uri("/migrate/court/repair/$prisonerNumber")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(CourtSchedulerMigrationFilter())
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.put().uri("/migrate/court/repair/$prisonerNumber")
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(CourtSchedulerMigrationFilter())
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access allowed with temporary role`() {
        webTestClient.put().uri("/migrate/court/repair/$prisonerNumber")
          .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_FROM_NOMIS__REPAIR_MOVEMENTS__RW")))
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(CourtSchedulerMigrationFilter())
          .exchange()
          .expectStatus().isOk
      }
    }

    private fun repairPrisoner(prisonerNumber: String) = webTestClient.put()
      .uri {
        it.path("/migrate/court/repair/$prisonerNumber")
          .build(prisonerNumber)
      }
      .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_FROM_NOMIS__MIGRATION__RW")))
      .contentType(MediaType.APPLICATION_JSON)
      .exchange()

    private fun repairPrisonerOk(prisonerNumber: String) = repairPrisoner(prisonerNumber).expectStatus().isOk
  }

  @Nested
  inner class Security {
    @Test
    fun `access forbidden when no role`() {
      webTestClient.post().uri("/migrate/court")
        .headers(setAuthorisation(roles = listOf()))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(CourtSchedulerMigrationFilter())
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `access forbidden with wrong role`() {
      webTestClient.post().uri("/migrate/court")
        .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(CourtSchedulerMigrationFilter())
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `access unauthorised with no auth token`() {
      webTestClient.post().uri("/migrate/court")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(CourtSchedulerMigrationFilter())
        .exchange()
        .expectStatus().isUnauthorized
    }
  }

  private fun performMigration(prisonerNumber: String? = null): String = webTestClient.post()
    .uri("/migrate/court")
    .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_FROM_NOMIS__MIGRATION__RW")))
    .contentType(MediaType.APPLICATION_JSON)
    .apply { prisonerNumber?.let { bodyValue("""{"prisonerNumber":"$prisonerNumber"}""") } ?: bodyValue("{}") }
    .exchange()
    .expectStatus().isAccepted
    .returnResult<MigrationResult>().responseBody.blockFirst()!!
    .migrationId
    .also {
      waitUntilCompleted()
    }

  private fun waitUntilCompleted(name: String = "court-movements-migration-completed") = await atMost Duration.ofSeconds(60) untilAsserted {
    verify(telemetryClient).trackEvent(
      eq(name),
      any(),
      isNull(),
    )
  }
}
