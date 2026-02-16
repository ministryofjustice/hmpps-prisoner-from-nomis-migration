package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements

import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.bookingMovedDomainEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.ExternalMovementsDpsApiExtension.Companion.dpsExtMovementsServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.MoveTemporaryAbsencesRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TemporaryAbsenceApplicationIdMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TemporaryAbsenceMoveBookingMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TemporaryAbsenceMovementIdMapping
import java.util.*

class ExternalMovementsMoveBookingIntTest(
  @Autowired private val mappingApi: ExternalMovementsMappingApiMockServer,
  @Autowired private val externalMovementsNomisApi: ExternalMovementsNomisApiMockServer,
) : SqsIntegrationTestBase() {

  private val dpsApi = dpsExtMovementsServer

  @Nested
  inner class HappyPath {
    private val applicationId1 = 1234L
    private val applicationId2 = 5678L
    private val authorisationId1 = UUID.randomUUID()
    private val authorisationId2 = UUID.randomUUID()
    private val movementSeq1 = 1
    private val movementSeq2 = 2
    private val scheduledMovementSeq1 = 3
    private val scheduledMovementSeq2 = 4
    private val movementId1 = UUID.randomUUID()
    private val movementId2 = UUID.randomUUID()
    private val scheduledMovementId1 = UUID.randomUUID()
    private val scheduledMovementId2 = UUID.randomUUID()

    private val moveBookingMappings = TemporaryAbsenceMoveBookingMappingDto(
      applicationIds = listOf(
        TemporaryAbsenceApplicationIdMapping(applicationId1, authorisationId1),
        TemporaryAbsenceApplicationIdMapping(applicationId2, authorisationId2),
      ),
      movementIds = listOf(
        TemporaryAbsenceMovementIdMapping(movementSeq1, movementId1),
        TemporaryAbsenceMovementIdMapping(movementSeq2, movementId2),
        TemporaryAbsenceMovementIdMapping(scheduledMovementSeq1, scheduledMovementId1),
        TemporaryAbsenceMovementIdMapping(scheduledMovementSeq2, scheduledMovementId2),
      ),
    )

    private val nomisData = externalMovementsNomisApi.temporaryAbsencesResponse(
      bookingId = 1234567L,
      applications = listOf(
        externalMovementsNomisApi.application(
          id = applicationId1,
          absences = listOf(
            externalMovementsNomisApi.absence(
              temporaryAbsence = externalMovementsNomisApi.temporaryAbsence(seq = scheduledMovementSeq1),
              temporaryAbsenceReturn = externalMovementsNomisApi.temporaryAbsenceReturn(seq = scheduledMovementSeq2),
            ),
          ),
        ),
        externalMovementsNomisApi.application(
          id = applicationId2,
          absences = listOf(externalMovementsNomisApi.absence()),
        ),
      ),
      unscheduledTemporaryAbsences = listOf(externalMovementsNomisApi.temporaryAbsence(seq = movementSeq1)),
      unscheduledTemporaryAbsenceReturns = listOf(externalMovementsNomisApi.temporaryAbsenceReturn(seq = movementSeq2)),
    )

    @BeforeEach
    fun setUp() {
      externalMovementsNomisApi.stubGetTemporaryAbsences("A1234KT", nomisData)
      mappingApi.stubGetMoveBookingMappings(1234567L, moveBookingMappings)
      dpsApi.stubMoveBooking()
      mappingApi.stubMoveBookingMappings(1234567L, "A1000KT", "A1234KT")

      sendMessage(
        bookingMovedDomainEvent(
          bookingId = 1234567,
          movedToNomsNumber = "A1234KT",
          movedFromNomsNumber = "A1000KT",
        ),
      )
        .also { waitForAnyProcessingToComplete() }
    }

    @Test
    fun `should get all movements from NOMIS`() {
      externalMovementsNomisApi.verifyGetTemporaryAbsences(offenderNo = "A1234KT")
    }

    @Test
    fun `should get move booking mappings`() {
      mappingApi.verify(getRequestedFor(urlEqualTo("/mapping/temporary-absence/move-booking/1234567")))
    }

    @Test
    fun `should move DPS IDs for applications and unscheduled movements`() {
      ExternalMovementsDpsApiMockServer.getRequestBody<MoveTemporaryAbsencesRequest>(
        putRequestedFor(urlEqualTo("/move/temporary-absences")),
      ).apply {
        assertThat(fromPersonIdentifier).isEqualTo("A1000KT")
        assertThat(toPersonIdentifier).isEqualTo("A1234KT")
        assertThat(authorisationIds).containsExactlyInAnyOrder(authorisationId1, authorisationId2)
        assertThat(unscheduledMovementIds).containsExactlyInAnyOrder(movementId1, movementId2)
      }
    }

    @Test
    fun `should move mappings to the new offender no`() {
      mappingApi.verify(putRequestedFor(urlEqualTo("/mapping/temporary-absence/move-booking/1234567/from/A1000KT/to/A1234KT")))
    }

    @Test
    fun `should publish telemetry`() {
      verify(telemetryClient).trackEvent(
        eq("temporary-absence-move-booking-success"),
        check {
          assertThat(it["bookingId"]).isEqualTo("1234567")
          assertThat(it["fromOffenderNo"]).isEqualTo("A1000KT")
          assertThat(it["toOffenderNo"]).isEqualTo("A1234KT")
          assertThat(it["nomisApplicationIds"]).contains("$applicationId1")
          assertThat(it["nomisApplicationIds"]).contains("$applicationId2")
          assertThat(it["nomisUnscheduledMovementSeqs"]).contains("$movementSeq1")
          assertThat(it["nomisUnscheduledMovementSeqs"]).contains("$movementSeq2")
          assertThat(it["nomisUnscheduledMovementSeqs"]).doesNotContain("$scheduledMovementSeq1")
          assertThat(it["nomisUnscheduledMovementSeqs"]).doesNotContain("$scheduledMovementSeq2")
          assertThat(it["dpsAuthorisationIds"]).contains("$authorisationId1")
          assertThat(it["dpsAuthorisationIds"]).contains("$authorisationId2")
          assertThat(it["dpsUnscheduledMovementIds"]).contains("$movementId1")
          assertThat(it["dpsUnscheduledMovementIds"]).contains("$movementId2")
          assertThat(it["dpsUnscheduledMovementIds"]).doesNotContain("$scheduledMovementId1")
          assertThat(it["dpsUnscheduledMovementIds"]).doesNotContain("$scheduledMovementId2")
        },
        isNull(),
      )
    }
  }

  @Nested
  inner class FailToFindNomisMappings {
    @BeforeEach
    fun setUp() {
      externalMovementsNomisApi.stubGetTemporaryAbsences(status = HttpStatus.NOT_FOUND)

      sendMessage(
        bookingMovedDomainEvent(
          bookingId = 1234567,
          movedToNomsNumber = "A1234KT",
          movedFromNomsNumber = "A1000KT",
        ),
      )
        .also { waitForAnyProcessingToComplete("temporary-absence-move-booking-error") }
    }

    @Test
    fun `should get all movements from NOMIS`() {
      externalMovementsNomisApi.verifyGetTemporaryAbsences(offenderNo = "A1234KT")
    }

    @Test
    fun `should publish error telemetry`() {
      verify(telemetryClient).trackEvent(
        eq("temporary-absence-move-booking-error"),
        check {
          assertThat(it["bookingId"]).isEqualTo("1234567")
          assertThat(it["fromOffenderNo"]).isEqualTo("A1000KT")
          assertThat(it["toOffenderNo"]).isEqualTo("A1234KT")
          assertThat(it["error"]).isEqualTo("No booking found in NOMIS for bookingId=1234567")
        },
        isNull(),
      )
    }
  }

  @Nested
  inner class NoMappingsExist {
    private val moveBookingMappings = TemporaryAbsenceMoveBookingMappingDto(
      applicationIds = listOf(),
      movementIds = listOf(),
    )

    private val nomisData = externalMovementsNomisApi.temporaryAbsencesResponse(
      bookingId = 1234567L,
      applications = listOf(),
      unscheduledTemporaryAbsences = listOf(),
      unscheduledTemporaryAbsenceReturns = listOf(),
    )

    @BeforeEach
    fun setUp() {
      externalMovementsNomisApi.stubGetTemporaryAbsences("A1234KT", nomisData)
      mappingApi.stubGetMoveBookingMappings(1234567L, moveBookingMappings)

      sendMessage(
        bookingMovedDomainEvent(
          bookingId = 1234567,
          movedToNomsNumber = "A1234KT",
          movedFromNomsNumber = "A1000KT",
        ),
      )
        .also { waitForAnyProcessingToComplete("temporary-absence-move-booking-ignored") }
    }

    @Test
    fun `should NOT call DPS`() {
      dpsApi.verify(0, putRequestedFor(urlEqualTo("/move/temporary-absences")))
    }

    @Test
    fun `should NOT move mappings to the new offender no`() {
      mappingApi.verify(0, putRequestedFor(urlEqualTo("/mapping/temporary-absence/move-booking/1234567/from/A1000KT/to/A1234KT")))
    }

    @Test
    fun `should publish telemetry`() {
      verify(telemetryClient).trackEvent(
        eq("temporary-absence-move-booking-ignored"),
        check {
          assertThat(it["bookingId"]).isEqualTo("1234567")
          assertThat(it["fromOffenderNo"]).isEqualTo("A1000KT")
          assertThat(it["toOffenderNo"]).isEqualTo("A1234KT")
          assertThat(it["reason"]).contains("No application or unscheduled mappings to move for booking=1234567")
        },
        isNull(),
      )
    }
  }

  @Nested
  inner class NomisMovementNotFoundInMappings {
    private val movementSeq1 = 1

    private val moveBookingMappings = TemporaryAbsenceMoveBookingMappingDto(
      applicationIds = listOf(),
      movementIds = listOf(),
    )

    private val nomisData = externalMovementsNomisApi.temporaryAbsencesResponse(
      bookingId = 1234567L,
      applications = listOf(),
      unscheduledTemporaryAbsences = listOf(externalMovementsNomisApi.temporaryAbsence(seq = movementSeq1)),
      unscheduledTemporaryAbsenceReturns = listOf(),
    )

    @BeforeEach
    fun setUp() {
      externalMovementsNomisApi.stubGetTemporaryAbsences("A1234KT", nomisData)
      mappingApi.stubGetMoveBookingMappings(1234567L, moveBookingMappings)

      sendMessage(
        bookingMovedDomainEvent(
          bookingId = 1234567,
          movedToNomsNumber = "A1234KT",
          movedFromNomsNumber = "A1000KT",
        ),
      )
        .also { waitForAnyProcessingToComplete("temporary-absence-move-booking-error") }
    }

    @Test
    fun `should NOT call DPS`() {
      dpsApi.verify(0, putRequestedFor(urlEqualTo("/move/temporary-absences")))
    }

    @Test
    fun `should NOT move mappings to the new offender no`() {
      mappingApi.verify(0, putRequestedFor(urlEqualTo("/mapping/temporary-absence/move-booking/1234567/from/A1000KT/to/A1234KT")))
    }

    @Test
    fun `should publish telemetry`() {
      verify(telemetryClient).trackEvent(
        eq("temporary-absence-move-booking-error"),
        check {
          assertThat(it["bookingId"]).isEqualTo("1234567")
          assertThat(it["fromOffenderNo"]).isEqualTo("A1000KT")
          assertThat(it["toOffenderNo"]).isEqualTo("A1234KT")
          assertThat(it["nomisApplicationIds"]).isEqualTo("[]")
          assertThat(it["nomisUnscheduledMovementSeqs"]).isEqualTo("[$movementSeq1]")
          assertThat(it["dpsAuthorisationIds"]).isEqualTo("[]")
          assertThat(it["error"]).isEqualTo("No mapping found for bookingId=1234567, movementSeq=1")
        },
        isNull(),
      )
    }
  }

  @Nested
  inner class NomisApplicationNotFoundInMappings {
    private val applicationId1 = 1234L

    private val moveBookingMappings = TemporaryAbsenceMoveBookingMappingDto(
      applicationIds = listOf(),
      movementIds = listOf(),
    )

    private val nomisData = externalMovementsNomisApi.temporaryAbsencesResponse(
      bookingId = 1234567L,
      applications = listOf(externalMovementsNomisApi.application(id = applicationId1)),
      unscheduledTemporaryAbsences = listOf(),
      unscheduledTemporaryAbsenceReturns = listOf(),
    )

    @BeforeEach
    fun setUp() {
      externalMovementsNomisApi.stubGetTemporaryAbsences("A1234KT", nomisData)
      mappingApi.stubGetMoveBookingMappings(1234567L, moveBookingMappings)

      sendMessage(
        bookingMovedDomainEvent(
          bookingId = 1234567,
          movedToNomsNumber = "A1234KT",
          movedFromNomsNumber = "A1000KT",
        ),
      )
        .also { waitForAnyProcessingToComplete("temporary-absence-move-booking-error") }
    }

    @Test
    fun `should NOT call DPS`() {
      dpsApi.verify(0, putRequestedFor(urlEqualTo("/move/temporary-absences")))
    }

    @Test
    fun `should NOT move mappings to the new offender no`() {
      mappingApi.verify(0, putRequestedFor(urlEqualTo("/mapping/temporary-absence/move-booking/1234567/from/A1000KT/to/A1234KT")))
    }

    @Test
    fun `should publish telemetry`() {
      verify(telemetryClient).trackEvent(
        eq("temporary-absence-move-booking-error"),
        check {
          assertThat(it["bookingId"]).isEqualTo("1234567")
          assertThat(it["fromOffenderNo"]).isEqualTo("A1000KT")
          assertThat(it["toOffenderNo"]).isEqualTo("A1234KT")
          assertThat(it["nomisApplicationIds"]).isEqualTo("[$applicationId1]")
          assertThat(it["error"]).isEqualTo("No mapping found for bookingId=1234567, movementApplicationId=1234")
        },
        isNull(),
      )
    }
  }

  @Nested
  inner class DpsUpdateFails {
    private val applicationId1 = 1234L
    private val authorisationId1 = UUID.randomUUID()

    private val moveBookingMappings = TemporaryAbsenceMoveBookingMappingDto(
      applicationIds = listOf(
        TemporaryAbsenceApplicationIdMapping(applicationId1, authorisationId1),
      ),
      movementIds = listOf(),
    )

    private val nomisData = externalMovementsNomisApi.temporaryAbsencesResponse(
      bookingId = 1234567L,
      applications = listOf(
        externalMovementsNomisApi.application(
          id = applicationId1,
          absences = listOf(),
        ),
      ),
      unscheduledTemporaryAbsences = listOf(),
      unscheduledTemporaryAbsenceReturns = listOf(),
    )

    @BeforeEach
    fun setUp() {
      externalMovementsNomisApi.stubGetTemporaryAbsences("A1234KT", nomisData)
      mappingApi.stubGetMoveBookingMappings(1234567L, moveBookingMappings)
      dpsApi.stubMoveBookingError(status = 500)

      sendMessage(
        bookingMovedDomainEvent(
          bookingId = 1234567,
          movedToNomsNumber = "A1234KT",
          movedFromNomsNumber = "A1000KT",
        ),
      )
        .also { waitForAnyProcessingToComplete("temporary-absence-move-booking-error") }
    }

    @Test
    fun `should attempt to move DPS IDs`() {
      dpsApi.verify(putRequestedFor(urlEqualTo("/move/temporary-absences")))
    }

    @Test
    fun `should publish telemetry`() {
      verify(telemetryClient).trackEvent(
        eq("temporary-absence-move-booking-error"),
        check {
          assertThat(it["bookingId"]).isEqualTo("1234567")
          assertThat(it["fromOffenderNo"]).isEqualTo("A1000KT")
          assertThat(it["toOffenderNo"]).isEqualTo("A1234KT")
          assertThat(it["nomisApplicationIds"]).isEqualTo("[$applicationId1]")
          assertThat(it["nomisUnscheduledMovementSeqs"]).isEqualTo("[]")
          assertThat(it["dpsAuthorisationIds"]).isEqualTo("[$authorisationId1]")
          assertThat(it["dpsUnscheduledMovementIds"]).isEqualTo("[]")
          assertThat(it["error"]).isEqualTo("500 Internal Server Error from PUT http://localhost:8103/move/temporary-absences")
        },
        isNull(),
      )
    }
  }

  @Nested
  inner class MappingUpdateShouldWorkOnRetry {
    private val applicationId1 = 1234L
    private val authorisationId1 = UUID.randomUUID()

    private val moveBookingMappings = TemporaryAbsenceMoveBookingMappingDto(
      applicationIds = listOf(
        TemporaryAbsenceApplicationIdMapping(applicationId1, authorisationId1),
      ),
      movementIds = listOf(),
    )

    private val nomisData = externalMovementsNomisApi.temporaryAbsencesResponse(
      bookingId = 1234567L,
      applications = listOf(
        externalMovementsNomisApi.application(
          id = applicationId1,
          absences = listOf(),
        ),
      ),
      unscheduledTemporaryAbsences = listOf(),
      unscheduledTemporaryAbsenceReturns = listOf(),
    )

    @BeforeEach
    fun setUp() {
      externalMovementsNomisApi.stubGetTemporaryAbsences("A1234KT", nomisData)
      mappingApi.stubGetMoveBookingMappings(1234567L, moveBookingMappings)
      dpsApi.stubMoveBooking()
      mappingApi.stubMoveBookingMappingsFailureFollowedBySuccess(1234567L, "A1000KT", "A1234KT")

      sendMessage(
        bookingMovedDomainEvent(
          bookingId = 1234567,
          movedToNomsNumber = "A1234KT",
          movedFromNomsNumber = "A1000KT",
        ),
      )
        .also { waitForAnyProcessingToComplete("temporary-absence-move-booking-mapping-retry-updated") }
    }

    @Test
    fun `should move DPS IDs for applications and unscheduled movements exactly once`() {
      dpsApi.verify(1, putRequestedFor(urlEqualTo("/move/temporary-absences")))
    }

    @Test
    fun `should move mappings to the new offender twice`() {
      mappingApi.verify(2, putRequestedFor(urlEqualTo("/mapping/temporary-absence/move-booking/1234567/from/A1000KT/to/A1234KT")))
    }

    @Test
    fun `should publish telemetry`() {
      verify(telemetryClient).trackEvent(
        eq("temporary-absence-move-booking-success"),
        check {
          assertThat(it["bookingId"]).isEqualTo("1234567")
          assertThat(it["fromOffenderNo"]).isEqualTo("A1000KT")
          assertThat(it["toOffenderNo"]).isEqualTo("A1234KT")
          assertThat(it["nomisApplicationIds"]).isEqualTo("[$applicationId1]")
          assertThat(it["nomisUnscheduledMovementSeqs"]).isEqualTo("[]")
          assertThat(it["dpsAuthorisationIds"]).isEqualTo("[$authorisationId1]")
          assertThat(it["dpsUnscheduledMovementIds"]).isEqualTo("[]")
        },
        isNull(),
      )
    }

    @Test
    fun `should publish mapping created telemetry`() {
      verify(telemetryClient).trackEvent(
        eq("temporary-absence-move-booking-mapping-retry-updated"),
        check {
          assertThat(it["bookingId"]).isEqualTo("1234567")
          assertThat(it["fromOffenderNo"]).isEqualTo("A1000KT")
          assertThat(it["toOffenderNo"]).isEqualTo("A1234KT")
          assertThat(it["nomisApplicationIds"]).isEqualTo("[$applicationId1]")
          assertThat(it["nomisUnscheduledMovementSeqs"]).isEqualTo("[]")
          assertThat(it["dpsAuthorisationIds"]).isEqualTo("[$authorisationId1]")
          assertThat(it["dpsUnscheduledMovementIds"]).isEqualTo("[]")
        },
        isNull(),
      )
    }
  }

  private fun sendMessage(event: String) = awsSqsExternalMovementsOffenderEventsClient.sendMessage(
    externalMovementsQueueOffenderEventsUrl,
    event,
  )
}
