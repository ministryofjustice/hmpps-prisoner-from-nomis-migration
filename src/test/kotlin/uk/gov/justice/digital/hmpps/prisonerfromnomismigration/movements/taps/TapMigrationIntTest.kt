package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.taps

import com.github.tomakehurst.wiremock.client.WireMock
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions
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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.ExternalMovementsMigrationFilter
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.ExternalMovementsNomisApiMockServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.ExternalMovementsNomisApiMockServer.Companion.application
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.ExternalMovementsNomisApiMockServer.Companion.temporaryAbsencesResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.MigrateTapMovement
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.MigrateTapRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.MigrateTapResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TemporaryAbsencesPrisonerMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TemporaryAbsencesPrisonerMappingIdsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderTemporaryAbsencesResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TapMigrationIntTest(
  @Autowired private val externalMovementsNomisApi: ExternalMovementsNomisApiMockServer,
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
        .bodyValue(ExternalMovementsMigrationFilter())
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `access forbidden with wrong role`() {
      webTestClient.post().uri("/migrate/taps")
        .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(ExternalMovementsMigrationFilter())
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `access unauthorised with no auth token`() {
      webTestClient.post().uri("/migrate/taps")
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
        mappingApi.stubGetTemporaryAbsenceMappingIds(prisonerNumber, 12345L, 1, dpsAuthorisationId, 1, dpsOccurrenceId, 3, dpsScheduledMovementOutId, 4, dpsScheduledMovementInId, 1, dpsUnscheduledMovementOutId, 2, dpsUnscheduledMovementInId)
        externalMovementsNomisApi.stubGetTemporaryAbsences(prisonerNumber)
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
      nomisApi.verify(WireMock.getRequestedFor(WireMock.urlPathEqualTo("/prisoners/ids/all")))
    }

    @Test
    fun `will request temporary absences for each prisoner`() {
      externalMovementsNomisApi.verifyGetTemporaryAbsences(offenderNo = "A0001KT")
      externalMovementsNomisApi.verifyGetTemporaryAbsences(offenderNo = "A0002KT")
    }

    @Test
    fun `will create mappings`() {
      mappingApi.verify(
        WireMock.putRequestedFor(WireMock.urlEqualTo("/mapping/temporary-absence/migrate"))
          .withRequestBodyJsonPath("prisonerNumber", "A0001KT")
          .withRequestBodyJsonPath("migrationId", migrationId),
      )
      mappingApi.verify(
        WireMock.putRequestedFor(WireMock.urlEqualTo("/mapping/temporary-absence/migrate"))
          .withRequestBodyJsonPath("prisonerNumber", "A0002KT")
          .withRequestBodyJsonPath("migrationId", migrationId),
      )
    }

    @Test
    fun `will call DPS for each offender`() {
      dpsApi.verify(
        WireMock.putRequestedFor(WireMock.urlEqualTo("/resync/temporary-absences/A0001KT")),
      )
      dpsApi.verify(
        WireMock.putRequestedFor(WireMock.urlEqualTo("/resync/temporary-absences/A0002KT")),
      )
    }

    @Test
    fun `will populate DPS TAP authorisation`() {
      getRequestBody<MigrateTapRequest>(
        WireMock.putRequestedFor(WireMock.urlEqualTo("/resync/temporary-absences/A0001KT")),
      ).apply {
        with(temporaryAbsences[0]) {
          Assertions.assertThat(prisonCode).isEqualTo("LEI")
          Assertions.assertThat(statusCode).isEqualTo("APPROVED")
          Assertions.assertThat(absenceTypeCode).isEqualTo("RR")
          Assertions.assertThat(absenceSubTypeCode).isEqualTo("SPL")
          Assertions.assertThat(accompaniedByCode).isEqualTo("U")
          Assertions.assertThat(transportCode).isEqualTo("VAN")
          Assertions.assertThat(repeat).isEqualTo(false)
          Assertions.assertThat(start).isEqualTo(today)
          Assertions.assertThat(end).isEqualTo(tomorrow)
          Assertions.assertThat(comments).isEqualTo("application comment")
          Assertions.assertThat(created.at.toLocalDate()).isEqualTo(today)
          Assertions.assertThat(created.by).isEqualTo("USER")
          Assertions.assertThat(updated).isNull()
          Assertions.assertThat(legacyId).isEqualTo(1)
          Assertions.assertThat(occurrences.size).isEqualTo(1)
          Assertions.assertThat(LocalTime.parse(startTime))
            .isCloseTo(now.minusDays(1).toLocalTime(), Assertions.within(Duration.ofMinutes(5)))
          Assertions.assertThat(LocalTime.parse(endTime))
            .isCloseTo(now.toLocalTime(), Assertions.within(Duration.ofMinutes(5)))
          Assertions.assertThat(location!!.uprn).isNull()
          Assertions.assertThat(location.address).isEqualTo("some full address")
          Assertions.assertThat(location.description).isEqualTo("some address description")
          Assertions.assertThat(location.postcode).isEqualTo("S1 1AA")
          Assertions.assertThat(id).isEqualTo(dpsAuthorisationId)
        }
      }
    }

    @Test
    fun `will populate DPS TAP occurrence`() {
      getRequestBody<MigrateTapRequest>(
        WireMock.putRequestedFor(WireMock.urlEqualTo("/resync/temporary-absences/A0001KT")),
      ).apply {
        with(temporaryAbsences[0].occurrences[0]) {
          Assertions.assertThat(isCancelled).isFalse
          Assertions.assertThat(start).isCloseTo(now.minusDays(1), Assertions.within(Duration.ofMinutes(5)))
          Assertions.assertThat(end).isCloseTo(now.plusDays(1), Assertions.within(Duration.ofMinutes(5)))
          Assertions.assertThat(location.address).isEqualTo("Schedule full address")
          Assertions.assertThat(location.description).isEqualTo("Schedule address description")
          Assertions.assertThat(location.postcode).isEqualTo("S1 1AA")
          Assertions.assertThat(location.uprn).isNull()
          Assertions.assertThat(absenceTypeCode).isEqualTo("RR")
          Assertions.assertThat(absenceSubTypeCode).isEqualTo("SPL")
          Assertions.assertThat(absenceReasonCode).isEqualTo("C5")
          Assertions.assertThat(accompaniedByCode).isEqualTo("PECS")
          Assertions.assertThat(transportCode).isEqualTo("VAN")
          Assertions.assertThat(contactInformation).isEqualTo("Derek")
          Assertions.assertThat(comments).isEqualTo("scheduled absence comment")
          Assertions.assertThat(created.at.toLocalDate()).isEqualTo(today)
          Assertions.assertThat(created.by).isEqualTo("USER")
          Assertions.assertThat(updated).isNull()
          Assertions.assertThat(legacyId).isEqualTo(1)
          Assertions.assertThat(movements.size).isEqualTo(2)
          Assertions.assertThat(id).isEqualTo(dpsOccurrenceId)
        }
      }
    }

    @Test
    fun `will populate DPS TAP OUT movement`() {
      getRequestBody<MigrateTapRequest>(
        WireMock.putRequestedFor(WireMock.urlEqualTo("/resync/temporary-absences/A0001KT")),
      ).apply {
        with(temporaryAbsences[0].occurrences[0].movements[0]) {
          Assertions.assertThat(occurredAt).isCloseTo(now.minusDays(1), Assertions.within(Duration.ofMinutes(5)))
          Assertions.assertThat(direction).isEqualTo(MigrateTapMovement.Direction.OUT)
          Assertions.assertThat(absenceReasonCode).isEqualTo("C6")
          Assertions.assertThat(location.address).isEqualTo("Absence full address")
          Assertions.assertThat(location.description).isEqualTo("Absence address description")
          Assertions.assertThat(location.postcode).isEqualTo("S1 1AA")
          Assertions.assertThat(location.uprn).isNull()
          Assertions.assertThat(accompaniedByCode).isEqualTo("U")
          Assertions.assertThat(created.at.toLocalDate()).isEqualTo(today)
          Assertions.assertThat(created.by).isEqualTo("USER")
          Assertions.assertThat(legacyId).isEqualTo("12345_3")
          Assertions.assertThat(accompaniedByComments).isEqualTo("Absence escort text")
          Assertions.assertThat(comments).isEqualTo("Absence comment text")
          Assertions.assertThat(updated).isNull()
          Assertions.assertThat(id).isEqualTo(dpsScheduledMovementOutId)
        }
      }
    }

    @Test
    fun `will populate DPS TAP IN movement`() {
      getRequestBody<MigrateTapRequest>(
        WireMock.putRequestedFor(WireMock.urlEqualTo("/resync/temporary-absences/A0001KT")),
      ).apply {
        with(temporaryAbsences[0].occurrences[0].movements[1]) {
          Assertions.assertThat(occurredAt).isCloseTo(now, Assertions.within(Duration.ofMinutes(5)))
          Assertions.assertThat(direction).isEqualTo(MigrateTapMovement.Direction.IN)
          Assertions.assertThat(absenceReasonCode).isEqualTo("C5")
          Assertions.assertThat(location.address).isEqualTo("Absence return full address")
          Assertions.assertThat(location.description).isEqualTo("Absence return address description")
          Assertions.assertThat(location.postcode).isEqualTo("S2 2AA")
          Assertions.assertThat(location.uprn).isNull()
          Assertions.assertThat(accompaniedByCode).isEqualTo("PECS")
          Assertions.assertThat(created.at.toLocalDate()).isEqualTo(today)
          Assertions.assertThat(created.by).isEqualTo("USER")
          Assertions.assertThat(legacyId).isEqualTo("12345_4")
          Assertions.assertThat(accompaniedByComments).isEqualTo("Return escort text")
          Assertions.assertThat(comments).isEqualTo("Return comment text")
          Assertions.assertThat(updated).isNull()
          Assertions.assertThat(id).isEqualTo(dpsScheduledMovementInId)
        }
      }
    }

    @Test
    fun `will populate unscheduled DPS TAP OUT movement`() {
      getRequestBody<MigrateTapRequest>(
        WireMock.putRequestedFor(WireMock.urlEqualTo("/resync/temporary-absences/A0001KT")),
      ).apply {
        with(unscheduledMovements[0]) {
          Assertions.assertThat(occurredAt).isCloseTo(now.minusDays(1), Assertions.within(Duration.ofMinutes(5)))
          Assertions.assertThat(direction).isEqualTo(MigrateTapMovement.Direction.OUT)
          Assertions.assertThat(absenceReasonCode).isEqualTo("C6")
          Assertions.assertThat(location.address).isEqualTo("Absence full address")
          Assertions.assertThat(location.description).isEqualTo("Absence address description")
          Assertions.assertThat(location.postcode).isEqualTo("S1 1AA")
          Assertions.assertThat(location.uprn).isNull()
          Assertions.assertThat(accompaniedByCode).isEqualTo("U")
          Assertions.assertThat(created.at.toLocalDate()).isEqualTo(today)
          Assertions.assertThat(created.by).isEqualTo("USER")
          Assertions.assertThat(legacyId).isEqualTo("12345_1")
          Assertions.assertThat(accompaniedByComments).isEqualTo("Absence escort text")
          Assertions.assertThat(comments).isEqualTo("Absence comment text")
          Assertions.assertThat(updated).isNull()
          Assertions.assertThat(id).isEqualTo(dpsUnscheduledMovementOutId)
        }
      }
    }

    @Test
    fun `will populate unscheduled DPS TAP IN movement`() {
      getRequestBody<MigrateTapRequest>(
        WireMock.putRequestedFor(WireMock.urlEqualTo("/resync/temporary-absences/A0001KT")),
      ).apply {
        with(unscheduledMovements[1]) {
          Assertions.assertThat(occurredAt).isCloseTo(now.minusDays(1), Assertions.within(Duration.ofMinutes(5)))
          Assertions.assertThat(direction).isEqualTo(MigrateTapMovement.Direction.IN)
          Assertions.assertThat(absenceReasonCode).isEqualTo("C5")
          Assertions.assertThat(location.address).isEqualTo("Absence return full address")
          Assertions.assertThat(location.description).isEqualTo("Absence return address description")
          Assertions.assertThat(location.postcode).isEqualTo("S2 2AA")
          Assertions.assertThat(location.uprn).isNull()
          Assertions.assertThat(accompaniedByCode).isEqualTo("PECS")
          Assertions.assertThat(created.at.toLocalDate()).isEqualTo(today)
          Assertions.assertThat(created.by).isEqualTo("USER")
          Assertions.assertThat(legacyId).isEqualTo("12345_2")
          Assertions.assertThat(accompaniedByComments).isEqualTo("Return escort text")
          Assertions.assertThat(comments).isEqualTo("Return comment text")
          Assertions.assertThat(updated).isNull()
          Assertions.assertThat(id).isEqualTo(dpsUnscheduledMovementInId)
        }
      }
    }

    @Test
    fun `will populate correct mapping details`() {
      ExternalMovementsMappingApiMockServer.getRequestBody<TemporaryAbsencesPrisonerMappingDto>(
        WireMock.putRequestedFor(WireMock.urlEqualTo("/mapping/temporary-absence/migrate")),
      )
        .apply {
          Assertions.assertThat(bookings[0].bookingId).isEqualTo(12345)

          Assertions.assertThat(bookings[0].applications[0].nomisMovementApplicationId).isEqualTo(1)
          Assertions.assertThat(bookings[0].applications[0].dpsMovementApplicationId).isEqualTo(dpsAuthorisationId)

          with(bookings[0].applications[0].schedules[0]) {
            Assertions.assertThat(nomisEventId).isEqualTo(1)
            Assertions.assertThat(dpsOccurrenceId).isEqualTo(dpsOccurrenceId)
            Assertions.assertThat(nomisAddressId).isEqualTo(543)
            Assertions.assertThat(nomisAddressOwnerClass).isEqualTo("CORP")
            Assertions.assertThat(dpsAddressText).isEqualTo("Schedule full address")
            Assertions.assertThat(dpsDescription).isEqualTo("Schedule address description")
            Assertions.assertThat(dpsPostcode).isEqualTo("S1 1AA")
            Assertions.assertThat(eventTime).contains("${today.minusDays(1)}")
          }

          // We don't map the scheduled return because they don't exist in DPS
          Assertions.assertThat(bookings[0].applications[0].schedules.size).isEqualTo(1)

          with(bookings[0].applications[0].movements[0]) {
            Assertions.assertThat(nomisMovementSeq).isEqualTo(3)
            Assertions.assertThat(dpsMovementId).isEqualTo(dpsScheduledMovementOutId)
            Assertions.assertThat(nomisAddressId).isEqualTo(432)
            Assertions.assertThat(nomisAddressOwnerClass).isEqualTo("AGY")
            Assertions.assertThat(dpsAddressText).isEqualTo("Absence full address")
            Assertions.assertThat(dpsDescription).isEqualTo("Absence address description")
            Assertions.assertThat(dpsPostcode).isEqualTo("S1 1AA")
          }

          with(bookings[0].applications[0].movements[1]) {
            Assertions.assertThat(nomisMovementSeq).isEqualTo(4)
            Assertions.assertThat(dpsMovementId).isEqualTo(dpsScheduledMovementInId)
            Assertions.assertThat(nomisAddressId).isEqualTo(321)
            Assertions.assertThat(nomisAddressOwnerClass).isEqualTo("CORP")
            Assertions.assertThat(dpsAddressText).isEqualTo("Absence return full address")
          }

          with(bookings[0].unscheduledMovements[0]) {
            Assertions.assertThat(nomisMovementSeq).isEqualTo(1)
            Assertions.assertThat(dpsMovementId).isEqualTo(dpsUnscheduledMovementOutId)
            Assertions.assertThat(nomisAddressId).isEqualTo(432)
            Assertions.assertThat(nomisAddressOwnerClass).isEqualTo("AGY")
            Assertions.assertThat(dpsAddressText).isEqualTo("Absence full address")
          }

          with(bookings[0].unscheduledMovements[1]) {
            Assertions.assertThat(nomisMovementSeq).isEqualTo(2)
            Assertions.assertThat(dpsMovementId).isEqualTo(dpsUnscheduledMovementInId)
            Assertions.assertThat(nomisAddressId).isEqualTo(321)
            Assertions.assertThat(nomisAddressOwnerClass).isEqualTo("CORP")
            Assertions.assertThat(dpsAddressText).isEqualTo("Absence return full address")
            Assertions.assertThat(dpsDescription).isEqualTo("Absence return address description")
            Assertions.assertThat(dpsPostcode).isEqualTo("S2 2AA")
          }
        }
    }

    @Test
    fun `will publish telemetry`() {
      verify(telemetryClient).trackEvent(
        eq("temporary-absences-migration-entity-migrated"),
        check {
          Assertions.assertThat(it["offenderNo"]).isEqualTo("A0001KT")
          Assertions.assertThat(it["migrationId"]).isEqualTo(migrationId)
        },
        isNull(),
      )
      verify(telemetryClient).trackEvent(
        eq("temporary-absences-migration-entity-migrated"),
        check {
          Assertions.assertThat(it["offenderNo"]).isEqualTo("A0002KT")
          Assertions.assertThat(it["migrationId"]).isEqualTo(migrationId)
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
      externalMovementsNomisApi.stubGetTemporaryAbsences(
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
        WireMock.putRequestedFor(WireMock.urlEqualTo("/resync/temporary-absences/$prisonerNumber")),
      ).apply {
        with(temporaryAbsences[0].occurrences[0].movements[0]) {
          Assertions.assertThat(this.prisonCode).isEqualTo(movementPrison)
        }
      }
    }

    @Test
    fun `will populate DPS TAP IN movement prison`() {
      getRequestBody<MigrateTapRequest>(
        WireMock.putRequestedFor(WireMock.urlEqualTo("/resync/temporary-absences/$prisonerNumber")),
      ).apply {
        with(temporaryAbsences[0].occurrences[0].movements[1]) {
          Assertions.assertThat(prisonCode).isEqualTo(movementPrison)
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
      externalMovementsNomisApi.stubGetTemporaryAbsences(
        prisonerNumber,
        response = temporaryAbsencesResponse(
          activeBooking = true,
          latestBooking = false,
          applications = listOf(
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
        WireMock.putRequestedFor(WireMock.urlEqualTo("/resync/temporary-absences/$prisonerNumber")),
      ).apply {
        with(temporaryAbsences[0]) {
          Assertions.assertThat(statusCode).isEqualTo("EXPIRED")
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
          Assertions.assertThat(it["offenderNo"]).isEqualTo("A0001KT")
          Assertions.assertThat(it["migrationId"]).isEqualTo(migrationId)
          Assertions.assertThat(it["reason"])
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
      externalMovementsNomisApi.verifyGetTemporaryAbsences(offenderNo = "A0001KT")
    }

    @Test
    fun `will create mappings twice before succeeding`() {
      mappingApi.verify(
        2,
        WireMock.putRequestedFor(WireMock.urlEqualTo("/mapping/temporary-absence/migrate"))
          .withRequestBodyJsonPath("prisonerNumber", "A0001KT")
          .withRequestBodyJsonPath("migrationId", migrationId),
      )
    }

    @Test
    fun `will publish telemetry once`() {
      verify(telemetryClient, times(1)).trackEvent(
        eq("temporary-absences-migration-entity-migrated"),
        check {
          Assertions.assertThat(it["offenderNo"]).isEqualTo("A0001KT")
          Assertions.assertThat(it["migrationId"]).isEqualTo(migrationId)
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
      externalMovementsNomisApi.stubGetTemporaryAbsences(
        "A0001KT",
        response = OffenderTemporaryAbsencesResponse(bookings = listOf()),
      )

      migrationId = performMigration()
    }

    @Test
    fun `will not migrate to DPS`() {
      dpsApi.verify(
        0,
        WireMock.putRequestedFor(WireMock.urlEqualTo("/resync/temporary-absences/A0001KT")),
      )
    }

    @Test
    fun `will publish ignore telemetry`() {
      verify(telemetryClient).trackEvent(
        eq("temporary-absences-migration-entity-ignored"),
        check {
          Assertions.assertThat(it["offenderNo"]).isEqualTo("A0001KT")
          Assertions.assertThat(it["migrationId"]).isEqualTo(migrationId)
          Assertions.assertThat(it["reason"]).isEqualTo("The offender has no TAPs")
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
        externalMovementsNomisApi.verifyGetTemporaryAbsences(offenderNo = "A0001KT")
      }

      @Test
      fun `will create mappings`() {
        mappingApi.verify(
          WireMock.putRequestedFor(WireMock.urlEqualTo("/mapping/temporary-absence/migrate"))
            .withRequestBodyJsonPath("prisonerNumber", "A0001KT"),
        )
      }

      @Test
      fun `will migrate to DPS`() {
        dpsApi.verify(WireMock.putRequestedFor(WireMock.urlEqualTo("/resync/temporary-absences/A0001KT")))
      }

      @Test
      fun `will publish telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absences-migration-entity-repair-requested"),
          check {
            Assertions.assertThat(it["offenderNo"]).isEqualTo("A0001KT")
          },
          isNull(),
        )
        verify(telemetryClient).trackEvent(
          eq("temporary-absences-migration-entity-migrated"),
          check {
            Assertions.assertThat(it["offenderNo"]).isEqualTo("A0001KT")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class DontIgnoreOffendersWithNoMovements {
      @BeforeEach
      fun setUp() = runTest {
        externalMovementsNomisApi.stubGetTemporaryAbsences(
          "A0001KT",
          response = OffenderTemporaryAbsencesResponse(bookings = listOf()),
        )
        dpsApi.stubResyncPrisonerTaps("A0001KT", response = MigrateTapResponse(listOf(), listOf()))

        repairPrisonerOk(prisonerNumber)
      }

      @Test
      fun `will migrate to DPS`() {
        dpsApi.verify(WireMock.putRequestedFor(WireMock.urlEqualTo("/resync/temporary-absences/A0001KT")))
      }

      @Test
      fun `will publish telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absences-migration-entity-repair-requested"),
          check {
            Assertions.assertThat(it["offenderNo"]).isEqualTo("A0001KT")
          },
          isNull(),
        )
        verify(telemetryClient).trackEvent(
          eq("temporary-absences-migration-entity-migrated"),
          check {
            Assertions.assertThat(it["offenderNo"]).isEqualTo("A0001KT")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class DontIgnoreOffendersNotInNomis {
      @BeforeEach
      fun setUp() = runTest {
        externalMovementsNomisApi.stubGetTemporaryAbsences(status = HttpStatus.NOT_FOUND)
        dpsApi.stubResyncPrisonerTapsError("A0001KT", status = 404)

        repairPrisonerOk(prisonerNumber)
      }

      @Test
      fun `will migrate to DPS`() {
        dpsApi.verify(WireMock.putRequestedFor(WireMock.urlEqualTo("/resync/temporary-absences/A0001KT")))
      }

      @Test
      fun `will update mappings`() {
        mappingApi.verify(
          WireMock.putRequestedFor(WireMock.urlEqualTo("/mapping/temporary-absence/migrate"))
            .withRequestBodyJsonPath("prisonerNumber", "A0001KT")
            .withRequestBodyJsonPath("bookings.length()", 0),
        )
      }

      @Test
      fun `will publish telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absences-migration-entity-repair-requested"),
          check {
            Assertions.assertThat(it["offenderNo"]).isEqualTo("A0001KT")
          },
          isNull(),
        )
        verify(telemetryClient).trackEvent(
          eq("temporary-absences-migration-entity-migrated"),
          check {
            Assertions.assertThat(it["offenderNo"]).isEqualTo("A0001KT")
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
          .bodyValue(ExternalMovementsMigrationFilter())
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.put().uri("/migrate/taps/repair/$prisonerNumber")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(ExternalMovementsMigrationFilter())
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.put().uri("/migrate/taps/repair/$prisonerNumber")
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(ExternalMovementsMigrationFilter())
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access allowed with temporary role`() {
        webTestClient.put().uri("/migrate/taps/repair/$prisonerNumber")
          .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_FROM_NOMIS__REPAIR_MOVEMENTS__RW")))
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(ExternalMovementsMigrationFilter())
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
