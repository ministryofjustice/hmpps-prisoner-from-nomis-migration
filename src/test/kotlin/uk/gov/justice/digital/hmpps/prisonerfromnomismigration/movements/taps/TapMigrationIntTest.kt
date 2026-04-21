package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.taps

import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
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
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.returnResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.MigrationResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.MigrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.ExternalMovementsDpsApiExtension.Companion.dpsExtMovementsServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.ExternalMovementsDpsApiMockServer.Companion.getRequestBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.ExternalMovementsDpsApiMockServer.Companion.migrateResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.ExternalMovementsMappingApiMockServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.MigrateTapMovement
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.MigrateTapRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.MigrateTapResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.taps.TapNomisApiMockServer.Companion.application
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.taps.TapNomisApiMockServer.Companion.temporaryAbsencesResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TemporaryAbsencesPrisonerMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TemporaryAbsencesPrisonerMappingIdsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderTapsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TapMigrationIntTest(
  @Autowired private val externalMovementsNomisApi: TapNomisApiMockServer,
  @Autowired private val mappingApi: ExternalMovementsMappingApiMockServer,
) : MigrationTestBase() {

  private val dpsApi = dpsExtMovementsServer

  private lateinit var migrationId: String
  private val now = LocalDateTime.now()
  private val today = LocalDate.now()
  private val tomorrow = today.plusDays(1)

  private val dpsAuthorisationId = UUID.randomUUID()
  private val dpsOccurrenceId = UUID.randomUUID()
  private val dpsScheduledMovementOutId = UUID.randomUUID()
  private val dpsScheduledMovementInId = UUID.randomUUID()
  private val dpsUnscheduledMovementOutId = UUID.randomUUID()
  private val dpsUnscheduledMovementInId = UUID.randomUUID()

  @AfterAll
  fun tearDownTelemetryClient() = reset(telemetryClient)

  @Nested
  inner class Security {
    @Test
    fun `access forbidden when no role`() {
      webTestClient.post().uri("/migrate/taps")
        .headers(setAuthorisation(roles = listOf()))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(TapMigrationFilter())
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `access forbidden with wrong role`() {
      webTestClient.post().uri("/migrate/taps")
        .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(TapMigrationFilter())
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `access unauthorised with no auth token`() {
      webTestClient.post().uri("/migrate/taps")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(TapMigrationFilter())
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
        mappingApi.stubGetTemporaryAbsenceMappingIds(prisonerNumber, 12345L, 1, dpsAuthorisationId, 1, dpsOccurrenceId, 3, dpsScheduledMovementOutId, 4, dpsScheduledMovementInId, 1, dpsUnscheduledMovementOutId, 2, dpsUnscheduledMovementInId)
        externalMovementsNomisApi.stubGetAllOffenderTaps(prisonerNumber)
        dpsApi.stubResyncPrisonerTaps(
          personIdentifier = prisonerNumber,
          response = migrateResponse(
            dpsAuthorisationId,
            dpsOccurrenceId,
            dpsScheduledMovementOutId,
            dpsScheduledMovementInId,
            dpsUnscheduledMovementOutId,
            dpsUnscheduledMovementInId,
          ),
        )
      }
  }

  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  inner class HappyPath {
    @BeforeAll
    fun setUp() = runTest {
      setupMigrationTest()

      stubMigrationDependencies()
      migrationId = performMigration()
    }

    @Test
    fun `will request all prisoner ids`() {
      nomisApi.verify(getRequestedFor(urlPathEqualTo("/prisoners/ids/all")))
    }

    @Test
    fun `will request temporary absences for each prisoner`() {
      externalMovementsNomisApi.verifyGetAllOffenderTaps(offenderNo = "A0001KT")
      externalMovementsNomisApi.verifyGetAllOffenderTaps(offenderNo = "A0002KT")
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
    fun `will call DPS for each offender`() {
      dpsApi.verify(
        putRequestedFor(urlEqualTo("/resync/temporary-absences/A0001KT")),
      )
      dpsApi.verify(
        putRequestedFor(urlEqualTo("/resync/temporary-absences/A0002KT")),
      )
    }

    @Test
    fun `will populate DPS TAP authorisation`() {
      getRequestBody<MigrateTapRequest>(
        putRequestedFor(urlEqualTo("/resync/temporary-absences/A0001KT")),
      ).apply {
        with(temporaryAbsences[0]) {
          assertThat(prisonCode).isEqualTo("LEI")
          assertThat(statusCode).isEqualTo("APPROVED")
          assertThat(absenceTypeCode).isEqualTo("RR")
          assertThat(absenceSubTypeCode).isEqualTo("SPL")
          assertThat(accompaniedByCode).isEqualTo("U")
          assertThat(transportCode).isEqualTo("VAN")
          assertThat(repeat).isEqualTo(false)
          assertThat(start).isEqualTo(today)
          assertThat(end).isEqualTo(tomorrow)
          assertThat(comments).isEqualTo("application comment")
          assertThat(created.at.toLocalDate()).isEqualTo(today)
          assertThat(created.by).isEqualTo("USER")
          assertThat(updated).isNull()
          assertThat(legacyId).isEqualTo(1)
          assertThat(occurrences.size).isEqualTo(1)
          assertThat(LocalTime.parse(startTime))
            .isCloseTo(now.minusDays(1).toLocalTime(), within(Duration.ofMinutes(5)))
          assertThat(LocalTime.parse(endTime))
            .isCloseTo(now.toLocalTime(), within(Duration.ofMinutes(5)))
          assertThat(location!!.uprn).isNull()
          assertThat(location.address).isEqualTo("some full address")
          assertThat(location.description).isEqualTo("some address description")
          assertThat(location.postcode).isEqualTo("S1 1AA")
          assertThat(id).isEqualTo(dpsAuthorisationId)
        }
      }
    }

    @Test
    fun `will populate DPS TAP occurrence`() {
      getRequestBody<MigrateTapRequest>(
        putRequestedFor(urlEqualTo("/resync/temporary-absences/A0001KT")),
      ).apply {
        with(temporaryAbsences[0].occurrences[0]) {
          assertThat(isCancelled).isFalse
          assertThat(start).isCloseTo(now.minusDays(1), within(Duration.ofMinutes(5)))
          assertThat(end).isCloseTo(now.plusDays(1), within(Duration.ofMinutes(5)))
          assertThat(location.address).isEqualTo("Schedule full address")
          assertThat(location.description).isEqualTo("Schedule address description")
          assertThat(location.postcode).isEqualTo("S1 1AA")
          assertThat(location.uprn).isNull()
          assertThat(absenceTypeCode).isEqualTo("RR")
          assertThat(absenceSubTypeCode).isEqualTo("SPL")
          assertThat(absenceReasonCode).isEqualTo("C5")
          assertThat(accompaniedByCode).isEqualTo("PECS")
          assertThat(transportCode).isEqualTo("VAN")
          assertThat(contactInformation).isEqualTo("Derek")
          assertThat(comments).isEqualTo("scheduled absence comment")
          assertThat(created.at.toLocalDate()).isEqualTo(today)
          assertThat(created.by).isEqualTo("USER")
          assertThat(updated).isNull()
          assertThat(legacyId).isEqualTo(1)
          assertThat(movements.size).isEqualTo(2)
          assertThat(id).isEqualTo(dpsOccurrenceId)
        }
      }
    }

    @Test
    fun `will populate DPS TAP OUT movement`() {
      getRequestBody<MigrateTapRequest>(
        putRequestedFor(urlEqualTo("/resync/temporary-absences/A0001KT")),
      ).apply {
        with(temporaryAbsences[0].occurrences[0].movements[0]) {
          assertThat(occurredAt).isCloseTo(now.minusDays(1), within(Duration.ofMinutes(5)))
          assertThat(direction).isEqualTo(MigrateTapMovement.Direction.OUT)
          assertThat(absenceReasonCode).isEqualTo("C6")
          assertThat(location.address).isEqualTo("Absence full address")
          assertThat(location.description).isEqualTo("Absence address description")
          assertThat(location.postcode).isEqualTo("S1 1AA")
          assertThat(location.uprn).isNull()
          assertThat(accompaniedByCode).isEqualTo("U")
          assertThat(created.at.toLocalDate()).isEqualTo(today)
          assertThat(created.by).isEqualTo("USER")
          assertThat(legacyId).isEqualTo("12345_3")
          assertThat(accompaniedByComments).isEqualTo("Absence escort text")
          assertThat(comments).isEqualTo("Absence comment text")
          assertThat(updated).isNull()
          assertThat(id).isEqualTo(dpsScheduledMovementOutId)
        }
      }
    }

    @Test
    fun `will populate DPS TAP IN movement`() {
      getRequestBody<MigrateTapRequest>(
        putRequestedFor(urlEqualTo("/resync/temporary-absences/A0001KT")),
      ).apply {
        with(temporaryAbsences[0].occurrences[0].movements[1]) {
          assertThat(occurredAt).isCloseTo(now, within(Duration.ofMinutes(5)))
          assertThat(direction).isEqualTo(MigrateTapMovement.Direction.IN)
          assertThat(absenceReasonCode).isEqualTo("C5")
          assertThat(location.address).isEqualTo("Absence return full address")
          assertThat(location.description).isEqualTo("Absence return address description")
          assertThat(location.postcode).isEqualTo("S2 2AA")
          assertThat(location.uprn).isNull()
          assertThat(accompaniedByCode).isEqualTo("PECS")
          assertThat(created.at.toLocalDate()).isEqualTo(today)
          assertThat(created.by).isEqualTo("USER")
          assertThat(legacyId).isEqualTo("12345_4")
          assertThat(accompaniedByComments).isEqualTo("Return escort text")
          assertThat(comments).isEqualTo("Return comment text")
          assertThat(updated).isNull()
          assertThat(id).isEqualTo(dpsScheduledMovementInId)
        }
      }
    }

    @Test
    fun `will populate unscheduled DPS TAP OUT movement`() {
      getRequestBody<MigrateTapRequest>(
        putRequestedFor(urlEqualTo("/resync/temporary-absences/A0001KT")),
      ).apply {
        with(unscheduledMovements[0]) {
          assertThat(occurredAt).isCloseTo(now.minusDays(1), within(Duration.ofMinutes(5)))
          assertThat(direction).isEqualTo(MigrateTapMovement.Direction.OUT)
          assertThat(absenceReasonCode).isEqualTo("C6")
          assertThat(location.address).isEqualTo("Absence full address")
          assertThat(location.description).isEqualTo("Absence address description")
          assertThat(location.postcode).isEqualTo("S1 1AA")
          assertThat(location.uprn).isNull()
          assertThat(accompaniedByCode).isEqualTo("U")
          assertThat(created.at.toLocalDate()).isEqualTo(today)
          assertThat(created.by).isEqualTo("USER")
          assertThat(legacyId).isEqualTo("12345_1")
          assertThat(accompaniedByComments).isEqualTo("Absence escort text")
          assertThat(comments).isEqualTo("Absence comment text")
          assertThat(updated).isNull()
          assertThat(id).isEqualTo(dpsUnscheduledMovementOutId)
        }
      }
    }

    @Test
    fun `will populate unscheduled DPS TAP IN movement`() {
      getRequestBody<MigrateTapRequest>(
        putRequestedFor(urlEqualTo("/resync/temporary-absences/A0001KT")),
      ).apply {
        with(unscheduledMovements[1]) {
          assertThat(occurredAt).isCloseTo(now.minusDays(1), within(Duration.ofMinutes(5)))
          assertThat(direction).isEqualTo(MigrateTapMovement.Direction.IN)
          assertThat(absenceReasonCode).isEqualTo("C5")
          assertThat(location.address).isEqualTo("Absence return full address")
          assertThat(location.description).isEqualTo("Absence return address description")
          assertThat(location.postcode).isEqualTo("S2 2AA")
          assertThat(location.uprn).isNull()
          assertThat(accompaniedByCode).isEqualTo("PECS")
          assertThat(created.at.toLocalDate()).isEqualTo(today)
          assertThat(created.by).isEqualTo("USER")
          assertThat(legacyId).isEqualTo("12345_2")
          assertThat(accompaniedByComments).isEqualTo("Return escort text")
          assertThat(comments).isEqualTo("Return comment text")
          assertThat(updated).isNull()
          assertThat(id).isEqualTo(dpsUnscheduledMovementInId)
        }
      }
    }

    @Test
    fun `will populate correct mapping details`() {
      ExternalMovementsMappingApiMockServer.getRequestBody<TemporaryAbsencesPrisonerMappingDto>(
        putRequestedFor(urlEqualTo("/mapping/temporary-absence/migrate")),
      )
        .apply {
          assertThat(bookings[0].bookingId).isEqualTo(12345)

          assertThat(bookings[0].applications[0].nomisMovementApplicationId).isEqualTo(1)
          assertThat(bookings[0].applications[0].dpsMovementApplicationId).isEqualTo(dpsAuthorisationId)

          with(bookings[0].applications[0].schedules[0]) {
            assertThat(nomisEventId).isEqualTo(1)
            assertThat(dpsOccurrenceId).isEqualTo(dpsOccurrenceId)
            assertThat(nomisAddressId).isEqualTo(543)
            assertThat(nomisAddressOwnerClass).isEqualTo("CORP")
            assertThat(dpsAddressText).isEqualTo("Schedule full address")
            assertThat(dpsDescription).isEqualTo("Schedule address description")
            assertThat(dpsPostcode).isEqualTo("S1 1AA")
            assertThat(eventTime).contains("${today.minusDays(1)}")
          }

          // We don't map the scheduled return because they don't exist in DPS
          assertThat(bookings[0].applications[0].schedules.size).isEqualTo(1)

          with(bookings[0].applications[0].movements[0]) {
            assertThat(nomisMovementSeq).isEqualTo(3)
            assertThat(dpsMovementId).isEqualTo(dpsScheduledMovementOutId)
            assertThat(nomisAddressId).isEqualTo(432)
            assertThat(nomisAddressOwnerClass).isEqualTo("AGY")
            assertThat(dpsAddressText).isEqualTo("Absence full address")
            assertThat(dpsDescription).isEqualTo("Absence address description")
            assertThat(dpsPostcode).isEqualTo("S1 1AA")
          }

          with(bookings[0].applications[0].movements[1]) {
            assertThat(nomisMovementSeq).isEqualTo(4)
            assertThat(dpsMovementId).isEqualTo(dpsScheduledMovementInId)
            assertThat(nomisAddressId).isEqualTo(321)
            assertThat(nomisAddressOwnerClass).isEqualTo("CORP")
            assertThat(dpsAddressText).isEqualTo("Absence return full address")
          }

          with(bookings[0].unscheduledMovements[0]) {
            assertThat(nomisMovementSeq).isEqualTo(1)
            assertThat(dpsMovementId).isEqualTo(dpsUnscheduledMovementOutId)
            assertThat(nomisAddressId).isEqualTo(432)
            assertThat(nomisAddressOwnerClass).isEqualTo("AGY")
            assertThat(dpsAddressText).isEqualTo("Absence full address")
          }

          with(bookings[0].unscheduledMovements[1]) {
            assertThat(nomisMovementSeq).isEqualTo(2)
            assertThat(dpsMovementId).isEqualTo(dpsUnscheduledMovementInId)
            assertThat(nomisAddressId).isEqualTo(321)
            assertThat(nomisAddressOwnerClass).isEqualTo("CORP")
            assertThat(dpsAddressText).isEqualTo("Absence return full address")
            assertThat(dpsDescription).isEqualTo("Absence return address description")
            assertThat(dpsPostcode).isEqualTo("S2 2AA")
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
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  inner class HappyPathMovedInDifferentPrison {
    val prisonerNumber = "A0001KT"
    val movementPrison = "MDI"

    @BeforeAll
    fun setUp() = runTest {
      setupMigrationTest()

      nomisApi.stubGetPrisonerIds(
        totalElements = 1,
        pageSize = 10,
        firstOffenderNo = prisonerNumber,
      )
      mappingApi.stubCreateTemporaryAbsenceMapping()
      mappingApi.stubGetTemporaryAbsenceMappingIds(
        prisonerNumber,
        idMappings = TemporaryAbsencesPrisonerMappingIdsDto(prisonerNumber, listOf(), listOf(), listOf()),
      )
      // The prison on the application and schedules is LEI
      externalMovementsNomisApi.stubGetAllOffenderTaps(
        prisonerNumber,
        response = temporaryAbsencesResponse(movementPrison = movementPrison),
      )
      dpsApi.stubResyncPrisonerTaps(
        personIdentifier = prisonerNumber,
        response = migrateResponse(
          dpsAuthorisationId,
          dpsOccurrenceId,
          dpsScheduledMovementOutId,
          dpsScheduledMovementInId,
          dpsUnscheduledMovementOutId,
          dpsUnscheduledMovementInId,
        ),
      )

      migrationId = performMigration()
    }

    @Test
    fun `will populate DPS TAP OUT movement prison`() {
      getRequestBody<MigrateTapRequest>(
        putRequestedFor(urlEqualTo("/resync/temporary-absences/$prisonerNumber")),
      ).apply {
        with(temporaryAbsences[0].occurrences[0].movements[0]) {
          assertThat(this.prisonCode).isEqualTo(movementPrison)
        }
      }
    }

    @Test
    fun `will populate DPS TAP IN movement prison`() {
      getRequestBody<MigrateTapRequest>(
        putRequestedFor(urlEqualTo("/resync/temporary-absences/$prisonerNumber")),
      ).apply {
        with(temporaryAbsences[0].occurrences[0].movements[1]) {
          assertThat(prisonCode).isEqualTo(movementPrison)
        }
      }
    }
  }

  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  inner class HappyPathOldBookingFutureApplication {
    val prisonerNumber = "A0001KT"

    @BeforeAll
    fun setUp() = runTest {
      setupMigrationTest()

      nomisApi.stubGetPrisonerIds(
        totalElements = 1,
        pageSize = 10,
        firstOffenderNo = prisonerNumber,
      )
      mappingApi.stubCreateTemporaryAbsenceMapping()
      mappingApi.stubGetTemporaryAbsenceMappingIds(
        prisonerNumber,
        idMappings = TemporaryAbsencesPrisonerMappingIdsDto(prisonerNumber, listOf(), listOf(), listOf()),
      )
      // The application is approved, on an inactive booking, and is not active
      externalMovementsNomisApi.stubGetAllOffenderTaps(
        prisonerNumber,
        response = temporaryAbsencesResponse(
          activeBooking = true,
          latestBooking = false,
          tapApplications = listOf(
            application(
              status = "APP-SCH",
              toDate = LocalDate.now(),
            ),
          ),
        ),
      )
      dpsApi.stubResyncPrisonerTaps(
        personIdentifier = prisonerNumber,
        response = migrateResponse(
          dpsAuthorisationId,
          dpsOccurrenceId,
          dpsScheduledMovementOutId,
          dpsScheduledMovementInId,
          dpsUnscheduledMovementOutId,
          dpsUnscheduledMovementInId,
        ),
      )

      migrationId = performMigration()
    }

    @Test
    fun `will set application status to expired`() {
      getRequestBody<MigrateTapRequest>(
        putRequestedFor(urlEqualTo("/resync/temporary-absences/$prisonerNumber")),
      ).apply {
        with(temporaryAbsences[0]) {
          assertThat(statusCode).isEqualTo("EXPIRED")
        }
      }
    }
  }

  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  inner class MigrateEntityFailure {
    @BeforeAll
    fun setUp() = runTest {
      setupMigrationTest()

      stubMigrationDependencies(entities = 1)
      dpsApi.stubResyncPrisonerTapsError("A0001KT", 400)
      migrationId = performMigration()
    }

    @Test
    fun `will publish error telemetry`() {
      verify(telemetryClient).trackEvent(
        eq("temporary-absences-migration-entity-failed"),
        check {
          assertThat(it["offenderNo"]).isEqualTo("A0001KT")
          assertThat(it["migrationId"]).isEqualTo(migrationId)
          assertThat(it["reason"])
            .isEqualTo("400 Bad Request from PUT http://localhost:8103/resync/temporary-absences/A0001KT")
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

      stubMigrationDependencies(1)
      mappingApi.stubCreateTemporaryAbsenceMappingFailureFollowedBySuccess()
      migrationId = performMigration()
    }

    @Test
    fun `will request temporary absences only once`() {
      externalMovementsNomisApi.verifyGetAllOffenderTaps(offenderNo = "A0001KT")
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

  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  inner class IgnoreOffendersWithNoMovements {
    @BeforeAll
    fun setUp() = runTest {
      setupMigrationTest()

      nomisApi.stubGetPrisonerIds(
        totalElements = 1,
        pageSize = 10,
        firstOffenderNo = "A0001KT",
      )
      mappingApi.stubGetTemporaryAbsenceMappingIds(
        "A0001KT",
        idMappings = TemporaryAbsencesPrisonerMappingIdsDto("A0001KT", listOf(), listOf(), listOf()),
      )
      externalMovementsNomisApi.stubGetAllOffenderTaps(
        "A0001KT",
        response = OffenderTapsResponse(bookings = listOf()),
      )

      migrationId = performMigration()
    }

    @Test
    fun `will not migrate to DPS`() {
      dpsApi.verify(
        0,
        putRequestedFor(urlEqualTo("/resync/temporary-absences/A0001KT")),
      )
    }

    @Test
    fun `will publish ignore telemetry`() {
      verify(telemetryClient).trackEvent(
        eq("temporary-absences-migration-entity-ignored"),
        check {
          assertThat(it["offenderNo"]).isEqualTo("A0001KT")
          assertThat(it["migrationId"]).isEqualTo(migrationId)
          assertThat(it["reason"]).isEqualTo("The offender has no TAPs")
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
      fun `will request temporary absences from NOMIS`() {
        externalMovementsNomisApi.verifyGetAllOffenderTaps(offenderNo = "A0001KT")
      }

      @Test
      fun `will create mappings`() {
        mappingApi.verify(
          putRequestedFor(urlEqualTo("/mapping/temporary-absence/migrate"))
            .withRequestBodyJsonPath("prisonerNumber", "A0001KT"),
        )
      }

      @Test
      fun `will migrate to DPS`() {
        dpsApi.verify(putRequestedFor(urlEqualTo("/resync/temporary-absences/A0001KT")))
      }

      @Test
      fun `will publish telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absences-migration-entity-repair-requested"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A0001KT")
          },
          isNull(),
        )
        verify(telemetryClient).trackEvent(
          eq("temporary-absences-migration-entity-migrated"),
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
        externalMovementsNomisApi.stubGetAllOffenderTaps(
          "A0001KT",
          response = OffenderTapsResponse(bookings = listOf()),
        )
        dpsApi.stubResyncPrisonerTaps("A0001KT", response = MigrateTapResponse(listOf(), listOf()))

        repairPrisonerOk(prisonerNumber)
      }

      @Test
      fun `will migrate to DPS`() {
        dpsApi.verify(putRequestedFor(urlEqualTo("/resync/temporary-absences/A0001KT")))
      }

      @Test
      fun `will publish telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absences-migration-entity-repair-requested"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A0001KT")
          },
          isNull(),
        )
        verify(telemetryClient).trackEvent(
          eq("temporary-absences-migration-entity-migrated"),
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
        externalMovementsNomisApi.stubGetAllOffenderTaps(status = HttpStatus.NOT_FOUND)
        dpsApi.stubResyncPrisonerTapsError("A0001KT", status = 404)

        repairPrisonerOk(prisonerNumber)
      }

      @Test
      fun `will migrate to DPS`() {
        dpsApi.verify(putRequestedFor(urlEqualTo("/resync/temporary-absences/A0001KT")))
      }

      @Test
      fun `will update mappings`() {
        mappingApi.verify(
          putRequestedFor(urlEqualTo("/mapping/temporary-absence/migrate"))
            .withRequestBodyJsonPath("prisonerNumber", "A0001KT")
            .withRequestBodyJsonPath("bookings.length()", 0),
        )
      }

      @Test
      fun `will publish telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absences-migration-entity-repair-requested"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A0001KT")
          },
          isNull(),
        )
        verify(telemetryClient).trackEvent(
          eq("temporary-absences-migration-entity-migrated"),
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
        webTestClient.put().uri("/migrate/taps/repair/$prisonerNumber")
          .headers(setAuthorisation(roles = listOf()))
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(TapMigrationFilter())
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.put().uri("/migrate/taps/repair/$prisonerNumber")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(TapMigrationFilter())
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.put().uri("/migrate/taps/repair/$prisonerNumber")
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(TapMigrationFilter())
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access allowed with temporary role`() {
        webTestClient.put().uri("/migrate/taps/repair/$prisonerNumber")
          .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_FROM_NOMIS__REPAIR_MOVEMENTS__RW")))
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(TapMigrationFilter())
          .exchange()
          .expectStatus().isOk
      }
    }

    private fun repairPrisoner(prisonerNumber: String) = webTestClient.put()
      .uri {
        it.path("/migrate/taps/repair/$prisonerNumber")
          .build(prisonerNumber)
      }
      .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_FROM_NOMIS__MIGRATION__RW")))
      .contentType(MediaType.APPLICATION_JSON)
      .exchange()

    private fun repairPrisonerOk(prisonerNumber: String) = repairPrisoner(prisonerNumber).expectStatus().isOk
  }

  private fun performMigration(prisonerNumber: String? = null): String = webTestClient.post()
    .uri("/migrate/taps")
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
