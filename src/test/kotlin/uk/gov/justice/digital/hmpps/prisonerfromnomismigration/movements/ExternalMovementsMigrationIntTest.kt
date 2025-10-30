package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements

import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.atMost
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.returnResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.MigrationResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TemporaryAbsencesPrisonerMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistoryRepository
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath
import java.time.Duration
import java.time.LocalDate

class ExternalMovementsMigrationIntTest(
  @Autowired private val migrationHistoryRepository: MigrationHistoryRepository,
  @Autowired private val externalMovementsNomisApi: ExternalMovementsNomisApiMockServer,
  @Autowired private val mappingApi: ExternalMovementsMappingApiMockServer,
) : SqsIntegrationTestBase() {

  private lateinit var migrationId: String

  @BeforeEach
  fun deleteHistoryRecords() = runTest {
    migrationHistoryRepository.deleteAll()
  }

  @Nested
  inner class Security {
    @Test
    fun `access forbidden when no role`() {
      webTestClient.post().uri("/migrate/external-movements")
        .headers(setAuthorisation(roles = listOf()))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(ExternalMovementsMigrationFilter())
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `access forbidden with wrong role`() {
      webTestClient.post().uri("/migrate/external-movements")
        .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(ExternalMovementsMigrationFilter())
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `access unauthorised with no auth token`() {
      webTestClient.post().uri("/migrate/external-movements")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(ExternalMovementsMigrationFilter())
        .exchange()
        .expectStatus().isUnauthorized
    }
  }

  private fun stubMigrationDependencies(entities: Int = 2) {
    nomisApi.stubGetPrisonerIds(totalElements = entities.toLong(), pageSize = 10, firstOffenderNo = "A0001KT")
    mappingApi.stubCreateTemporaryAbsenceMapping()
    (1..entities)
      .map { index -> "A%04dKT".format(index) }
      .forEach { prisonerNumber ->
        mappingApi.stubGetTemporaryAbsenceMappings(prisonerNumber, NOT_FOUND)
        externalMovementsNomisApi.stubGetTemporaryAbsences(prisonerNumber)
      }
  }

  @Nested
  inner class HappyPath {
    @BeforeEach
    fun setUp() = runTest {
      stubMigrationDependencies()
      migrationId = performMigration()
    }

    @Test
    fun `will request all prisoner ids`() {
      nomisApi.verify(getRequestedFor(urlPathEqualTo("/prisoners/ids/all")))
    }

    @Test
    fun `will request temporary absences for each prisoner`() {
      externalMovementsNomisApi.verifyGetTemporaryAbsences(offenderNo = "A0001KT")
      externalMovementsNomisApi.verifyGetTemporaryAbsences(offenderNo = "A0002KT")
    }

    @Test
    fun `will check mappings`() {
      mappingApi.verify(
        getRequestedFor(urlEqualTo("/mapping/temporary-absence/nomis-prisoner-number/A0001KT")),
      )
      mappingApi.verify(
        getRequestedFor(urlEqualTo("/mapping/temporary-absence/nomis-prisoner-number/A0002KT")),
      )
    }

    @Test
    fun `will create mappings`() {
      mappingApi.verify(
        putRequestedFor(urlEqualTo("/mapping/temporary-absence/migrate"))
          .withRequestBodyJsonPath("prisonerNumber", "A0001KT")
          .withRequestBodyJsonPath("migrationId", migrationId),
      )
      mappingApi.verify(
        putRequestedFor(urlEqualTo("/mapping/temporary-absence/migrate"))
          .withRequestBodyJsonPath("prisonerNumber", "A0002KT")
          .withRequestBodyJsonPath("migrationId", migrationId),
      )
    }

    @Test
    fun `will populate correct mapping details`() {
      ExternalMovementsMappingApiMockServer.getRequestBody<TemporaryAbsencesPrisonerMappingDto>(
        putRequestedFor(urlEqualTo("/mapping/temporary-absence/migrate")),
      )
        .apply {
          assertThat(bookings[0].bookingId).isEqualTo(12345)

          assertThat(bookings[0].applications[0].nomisMovementApplicationId).isEqualTo(1)
          // TODO check DPS application ID "bookings[0].applications[0].dpsApplicationId"

          with(bookings[0].applications[0].schedules[0]) {
            assertThat(nomisEventId).isEqualTo(1)
            // TODO check DPS occurrence ID
            assertThat(nomisAddressId).isEqualTo(543)
            assertThat(nomisAddressOwnerClass).isEqualTo("OFF")
            assertThat(dpsAddressText).isEqualTo("Schedule full address")
            assertThat(eventTime).contains("${LocalDate.now()}")
          }

          // We don't map the scheduled return because they don't exist in DPS
          assertThat(bookings[0].applications[0].schedules.size).isEqualTo(1)

          with(bookings[0].applications[0].movements[0]) {
            assertThat(nomisMovementSeq).isEqualTo(3)
            // TODO check DPS movement ID
            assertThat(nomisAddressId).isEqualTo(432)
            assertThat(nomisAddressOwnerClass).isEqualTo("AGY")
            assertThat(dpsAddressText).isEqualTo("Absence full address")
          }

          with(bookings[0].applications[0].movements[1]) {
            // TODO check DPS movement ID
            assertThat(nomisMovementSeq).isEqualTo(4)
            assertThat(nomisAddressId).isEqualTo(321)
            assertThat(nomisAddressOwnerClass).isEqualTo("CORP")
            assertThat(dpsAddressText).isEqualTo("Absence return full address")
          }

          with(bookings[0].unscheduledMovements[0]) {
            assertThat(nomisMovementSeq).isEqualTo(1)
            // TODO check DPS movement ID
            assertThat(nomisAddressId).isEqualTo(432)
            assertThat(nomisAddressOwnerClass).isEqualTo("AGY")
            assertThat(dpsAddressText).isEqualTo("Absence full address")
          }

          with(bookings[0].unscheduledMovements[1]) {
            assertThat(nomisMovementSeq).isEqualTo(2)
            // TODO check DPS movement ID
            assertThat(nomisAddressId).isEqualTo(321)
            assertThat(nomisAddressOwnerClass).isEqualTo("CORP")
            assertThat(dpsAddressText).isEqualTo("Absence return full address")
          }
        }
    }

    @Test
    fun `will publish telemetry`() {
      verify(telemetryClient).trackEvent(
        eq("temporary-absences-migration-entity-migrated"),
        check {
          assertThat(it["offenderNo"]).isEqualTo("A0001KT")
          assertThat(it["migrationId"]).isEqualTo(migrationId)
        },
        isNull(),
      )
      verify(telemetryClient).trackEvent(
        eq("temporary-absences-migration-entity-migrated"),
        check {
          assertThat(it["offenderNo"]).isEqualTo("A0002KT")
          assertThat(it["migrationId"]).isEqualTo(migrationId)
        },
        isNull(),
      )
    }
  }

  @Nested
  inner class MappingErrorRecovery {
    @BeforeEach
    fun setUp() = runTest {
      stubMigrationDependencies(1)
      mappingApi.stubCreateTemporaryAbsenceMappingFailureFollowedBySuccess()
      migrationId = performMigration()
    }

    @Test
    fun `will request temporary absences only once`() {
      externalMovementsNomisApi.verifyGetTemporaryAbsences(offenderNo = "A0001KT")
    }

    @Test
    fun `will create mappings twice before succeeding`() {
      mappingApi.verify(
        2,
        putRequestedFor(urlEqualTo("/mapping/temporary-absence/migrate"))
          .withRequestBodyJsonPath("prisonerNumber", "A0001KT")
          .withRequestBodyJsonPath("migrationId", migrationId),
      )
    }

    @Test
    fun `will publish telemetry once`() {
      verify(telemetryClient, times(1)).trackEvent(
        eq("temporary-absences-migration-entity-migrated"),
        check {
          assertThat(it["offenderNo"]).isEqualTo("A0001KT")
          assertThat(it["migrationId"]).isEqualTo(migrationId)
        },
        isNull(),
      )
    }
  }

  private fun performMigration(prisonerNumber: String? = null): String = webTestClient.post()
    .uri("/migrate/external-movements")
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

  private fun waitUntilCompleted() = await atMost Duration.ofSeconds(60) untilAsserted {
    verify(telemetryClient).trackEvent(
      eq("temporary-absences-migration-completed"),
      any(),
      isNull(),
    )
  }
}
