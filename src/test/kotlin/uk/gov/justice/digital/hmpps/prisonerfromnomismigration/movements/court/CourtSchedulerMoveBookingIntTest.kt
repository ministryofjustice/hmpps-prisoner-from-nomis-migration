package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.court

import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.http.HttpStatus.NOT_FOUND
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtscheduler.model.MoveCourtEventRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.bookingMovedDomainEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.court.CourtSchedulerDpsApiExtension.Companion.dpsCourtSchedulerServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.court.CourtSchedulerDpsApiMockServer.Companion.getRequestBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.court.CourtSchedulerNomisApiMockServer.Companion.bookingCourtMovementIn
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.court.CourtSchedulerNomisApiMockServer.Companion.bookingCourtMovementOut
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.court.CourtSchedulerNomisApiMockServer.Companion.bookingCourtSchedule
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtMovementIdMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtScheduleIdMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtSchedulerMoveBookingMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.BookingCourtMovements
import java.util.*

class CourtSchedulerMoveBookingIntTest(
  @Autowired private val mappingApi: CourtSchedulerMappingApiMockServer,
  @Autowired private val courtSchedulerNomisApi: CourtSchedulerNomisApiMockServer,
) : CourtSchedulerIntegrationTestBase() {

  private val dpsApi = dpsCourtSchedulerServer

  @Nested
  inner class HappyPath {
    private val eventId1 = 123L
    private val dpsScheduleId1 = UUID.randomUUID()
    private val movementSeq1 = 1
    private val movementSeq2 = 2
    private val scheduledMovementSeq1 = 3
    private val scheduledMovementSeq2 = 4
    private val dpsMovementId1 = UUID.randomUUID()
    private val dpsMovementId2 = UUID.randomUUID()
    private val dpsScheduledMovementId1 = UUID.randomUUID()
    private val dpsScheduledMovementId2 = UUID.randomUUID()

    private val moveBookingMappings = CourtSchedulerMoveBookingMappingDto(
      scheduleIds = listOf(
        CourtScheduleIdMapping(eventId1, dpsScheduleId1),
      ),
      movementIds = listOf(
        CourtMovementIdMapping(movementSeq1, dpsMovementId1),
        CourtMovementIdMapping(movementSeq2, dpsMovementId2),
        CourtMovementIdMapping(scheduledMovementSeq1, dpsScheduledMovementId1),
        CourtMovementIdMapping(scheduledMovementSeq2, dpsScheduledMovementId2),
      ),
    )

    private val nomisData = BookingCourtMovements(
      bookingId = 12345L,
      activeBooking = true,
      latestBooking = true,
      courtSchedules = listOf(
        bookingCourtSchedule(eventId = eventId1, movementOutSeq = scheduledMovementSeq1, movementInSeq = scheduledMovementSeq2),
      ),
      unscheduledCourtMovementOuts = listOf(bookingCourtMovementOut(seq = movementSeq1)),
      unscheduledCourtMovementIns = listOf(bookingCourtMovementIn(seq = movementSeq2)),
    )

    @BeforeEach
    fun setUp() = runTest {
      courtSchedulerNomisApi.stubGetBookingCourtMovements(12345L, nomisData)
      mappingApi.stubGetMoveBookingMappings(12345L, moveBookingMappings)
      dpsApi.stubMoveBooking()
      mappingApi.stubMoveBookingMappings(bookingId = 12345L, fromOffenderNo = "A1000KT", toOffenderNo = "A1234KT")

      // Also need stubs for the calls to resync the prisoner. This bypasses the resync process because stubbing all of the various API calls makes the test data setup even more unreadable.
      doNothing().whenever(courtSchedulerMigrationService).resyncPrisonerCourtMovements(any())

      sendMessage(
        bookingMovedDomainEvent(
          bookingId = 12345,
          movedToNomsNumber = "A1234KT",
          movedFromNomsNumber = "A1000KT",
        ),
      )
        .also { waitForAnyProcessingToComplete() }
    }

    @Test
    fun `should get all movements from NOMIS`() {
      courtSchedulerNomisApi.verifyGetBookingCourtMovements(bookingId = 12345L)
    }

    @Test
    fun `should get move booking mappings`() {
      mappingApi.verify(getRequestedFor(urlEqualTo("/mapping/court-scheduler/move-booking/12345")))
    }

    @Test
    fun `should move DPS IDs for applications and unscheduled movements`() {
      getRequestBody<MoveCourtEventRequest>(
        putRequestedFor(urlEqualTo("/move/court-appearances")),
      ).apply {
        assertThat(fromPersonIdentifier).isEqualTo("A1000KT")
        assertThat(toPersonIdentifier).isEqualTo("A1234KT")
        assertThat(scheduleIds).containsExactlyInAnyOrder(dpsScheduleId1)
        assertThat(unscheduledMovementIds).containsExactlyInAnyOrder(dpsMovementId1, dpsMovementId2)
      }
    }

    @Test
    fun `should move mappings to the new offender no`() {
      mappingApi.verify(putRequestedFor(urlEqualTo("/mapping/court-scheduler/move-booking/12345/from/A1000KT/to/A1234KT")))
    }

    @Test
    fun `should resync each offender`() = runTest {
      verify(courtSchedulerMigrationService).resyncPrisonerCourtMovements("A1000KT")
      verify(courtSchedulerMigrationService).resyncPrisonerCourtMovements("A1234KT")
    }

    @Test
    fun `should publish telemetry`() {
      verify(telemetryClient).trackEvent(
        eq("court-scheduler-move-booking-success"),
        check {
          assertThat(it["bookingId"]).isEqualTo("12345")
          assertThat(it["fromOffenderNo"]).isEqualTo("A1000KT")
          assertThat(it["toOffenderNo"]).isEqualTo("A1234KT")
          assertThat(it["nomisEventIds"]).contains("123")
          assertThat(it["nomisUnscheduledMovementSeqs"]).contains("1")
          assertThat(it["nomisUnscheduledMovementSeqs"]).contains("2")
          assertThat(it["dpsCourtAppearanceIds"]).contains("$dpsScheduleId1")
          assertThat(it["dpsUnscheduledCourtMovementIds"]).contains("$dpsMovementId1")
          assertThat(it["dpsUnscheduledCourtMovementIds"]).contains("$dpsMovementId2")
        },
        isNull(),
      )
    }
  }

  @Nested
  inner class PrisonerNotFound {
    @BeforeEach
    fun setUp() = runTest {
      courtSchedulerNomisApi.stubGetBookingCourtMovements(status = NOT_FOUND)

      sendMessage(
        bookingMovedDomainEvent(
          bookingId = 12345,
          movedToNomsNumber = "A1234KT",
          movedFromNomsNumber = "A1000KT",
        ),
      )
        .also { waitForAnyProcessingToComplete("court-scheduler-move-booking-ignored") }
    }

    @Test
    fun `should get all movements from NOMIS`() {
      courtSchedulerNomisApi.verifyGetBookingCourtMovements(bookingId = 12345L)
    }

    @Test
    fun `should NOT move mappings to the new offender no`() {
      mappingApi.verify(
        count = 0,
        putRequestedFor(urlEqualTo("/mapping/court-scheduler/move-booking/12345/from/A1000KT/to/A1234KT")),
      )
    }

    @Test
    fun `should publish ignore telemetry`() {
      verify(telemetryClient).trackEvent(
        eq("court-scheduler-move-booking-ignored"),
        check {
          assertThat(it["bookingId"]).isEqualTo("12345")
          assertThat(it["fromOffenderNo"]).isEqualTo("A1000KT")
          assertThat(it["toOffenderNo"]).isEqualTo("A1234KT")
        },
        isNull(),
      )
    }
  }

  @Nested
  inner class NothingToMove {
    @BeforeEach
    fun setUp() = runTest {
      courtSchedulerNomisApi.stubGetBookingCourtMovements(
        response =
        BookingCourtMovements(
          bookingId = 12345,
          activeBooking = true,
          latestBooking = true,
          courtSchedules = listOf(),
          unscheduledCourtMovementOuts = listOf(),
          unscheduledCourtMovementIns = listOf(),
        ),
      )

      sendMessage(
        bookingMovedDomainEvent(
          bookingId = 12345,
          movedToNomsNumber = "A1234KT",
          movedFromNomsNumber = "A1000KT",
        ),
      )
        .also { waitForAnyProcessingToComplete("court-scheduler-move-booking-ignored") }
    }

    @Test
    fun `should get all movements from NOMIS`() {
      courtSchedulerNomisApi.verifyGetBookingCourtMovements(bookingId = 12345L)
    }

    @Test
    fun `should NOT move mappings to the new offender no`() {
      mappingApi.verify(
        count = 0,
        putRequestedFor(urlEqualTo("/mapping/court-scheduler/move-booking/12345/from/A1000KT/to/A1234KT")),
      )
    }

    @Test
    fun `should publish ignore telemetry`() {
      verify(telemetryClient).trackEvent(
        eq("court-scheduler-move-booking-ignored"),
        check {
          assertThat(it["bookingId"]).isEqualTo("12345")
          assertThat(it["fromOffenderNo"]).isEqualTo("A1000KT")
          assertThat(it["toOffenderNo"]).isEqualTo("A1234KT")
        },
        isNull(),
      )
    }
  }

  @Nested
  inner class NomisEventIdNotInMappings {
    @BeforeEach
    fun setUp() = runTest {
      courtSchedulerNomisApi.stubGetBookingCourtMovements(
        response =
        BookingCourtMovements(
          bookingId = 12345,
          activeBooking = true,
          latestBooking = true,
          courtSchedules = listOf(bookingCourtSchedule(eventId = 1, movementOutSeq = null, movementInSeq = null)),
          unscheduledCourtMovementOuts = listOf(),
          unscheduledCourtMovementIns = listOf(),
        ),
      )

      mappingApi.stubGetMoveBookingMappings(
        12345L,
        CourtSchedulerMoveBookingMappingDto(
          scheduleIds = listOf(),
          movementIds = listOf(),
        ),
      )

      sendMessage(
        bookingMovedDomainEvent(
          bookingId = 12345,
          movedToNomsNumber = "A1234KT",
          movedFromNomsNumber = "A1000KT",
        ),
      )
        .also { waitForAnyProcessingToComplete("court-scheduler-move-booking-error") }
    }

    @Test
    fun `should NOT move mappings to the new offender no`() {
      mappingApi.verify(
        count = 0,
        putRequestedFor(urlEqualTo("/mapping/court-scheduler/move-booking/12345/from/A1000KT/to/A1234KT")),
      )
    }

    @Test
    fun `should publish error telemetry`() {
      verify(telemetryClient).trackEvent(
        eq("court-scheduler-move-booking-error"),
        check {
          assertThat(it["bookingId"]).isEqualTo("12345")
          assertThat(it["fromOffenderNo"]).isEqualTo("A1000KT")
          assertThat(it["toOffenderNo"]).isEqualTo("A1234KT")
          assertThat(it["nomisEventIds"]).isEqualTo("[1]")
          assertThat(it["dpsCourtAppearanceIds"]).isNull()
          assertThat(it["error"]).isEqualTo("No court schedule mapping found for eventId=1")
        },
        isNull(),
      )
    }
  }

  @Nested
  inner class NomisMovementIdNotInMappings {
    @BeforeEach
    fun setUp() = runTest {
      courtSchedulerNomisApi.stubGetBookingCourtMovements(
        response =
        BookingCourtMovements(
          bookingId = 12345,
          activeBooking = true,
          latestBooking = true,
          courtSchedules = listOf(bookingCourtSchedule(eventId = 1, movementOutSeq = null, movementInSeq = null)),
          unscheduledCourtMovementOuts = listOf(bookingCourtMovementOut(2)),
          unscheduledCourtMovementIns = listOf(),
        ),
      )

      mappingApi.stubGetMoveBookingMappings(
        12345L,
        CourtSchedulerMoveBookingMappingDto(
          scheduleIds = listOf(CourtScheduleIdMapping(1, UUID.randomUUID())),
          movementIds = listOf(),
        ),
      )

      sendMessage(
        bookingMovedDomainEvent(
          bookingId = 12345,
          movedToNomsNumber = "A1234KT",
          movedFromNomsNumber = "A1000KT",
        ),
      )
        .also { waitForAnyProcessingToComplete("court-scheduler-move-booking-error") }
    }

    @Test
    fun `should NOT move mappings to the new offender no`() {
      mappingApi.verify(
        count = 0,
        putRequestedFor(urlEqualTo("/mapping/court-scheduler/move-booking/12345/from/A1000KT/to/A1234KT")),
      )
    }

    @Test
    fun `should publish error telemetry`() {
      verify(telemetryClient).trackEvent(
        eq("court-scheduler-move-booking-error"),
        check {
          assertThat(it["bookingId"]).isEqualTo("12345")
          assertThat(it["fromOffenderNo"]).isEqualTo("A1000KT")
          assertThat(it["toOffenderNo"]).isEqualTo("A1234KT")
          assertThat(it["nomisUnscheduledMovementSeqs"]).isEqualTo("[2]")
          assertThat(it["dpsUnscheduledCourtMovementIds"]).isNull()
          assertThat(it["error"]).isEqualTo("No court movement mapping found for bookingId=12345, movementSeq=2")
        },
        isNull(),
      )
    }
  }

  @Nested
  inner class DpsUpdateFails {
    private val eventId1 = 123L
    private val dpsScheduleId1 = UUID.randomUUID()

    private val moveBookingMappings = CourtSchedulerMoveBookingMappingDto(
      scheduleIds = listOf(
        CourtScheduleIdMapping(eventId1, dpsScheduleId1),
      ),
      movementIds = listOf(),
    )

    private val nomisData = BookingCourtMovements(
      bookingId = 12345L,
      activeBooking = true,
      latestBooking = true,
      courtSchedules = listOf(
        bookingCourtSchedule(eventId = eventId1, movementOutSeq = null, movementInSeq = null),
      ),
      unscheduledCourtMovementOuts = listOf(),
      unscheduledCourtMovementIns = listOf(),
    )

    @BeforeEach
    fun setUp() = runTest {
      courtSchedulerNomisApi.stubGetBookingCourtMovements(12345L, nomisData)
      mappingApi.stubGetMoveBookingMappings(12345L, moveBookingMappings)
      dpsApi.stubMoveBookingError(status = INTERNAL_SERVER_ERROR.value())
      mappingApi.stubMoveBookingMappings(bookingId = 12345L, fromOffenderNo = "A1000KT", toOffenderNo = "A1234KT")

      sendMessage(
        bookingMovedDomainEvent(
          bookingId = 12345,
          movedToNomsNumber = "A1234KT",
          movedFromNomsNumber = "A1000KT",
        ),
      )
        .also { waitForAnyProcessingToComplete("court-scheduler-move-booking-error") }
    }

    @Test
    fun `should calls DPS API`() {
      dpsApi.verify(
        putRequestedFor(urlEqualTo("/move/court-appearances")),
      )
    }

    @Test
    fun `should NOT move mappings to the new offender no`() {
      mappingApi.verify(
        count = 0,
        putRequestedFor(urlEqualTo("/mapping/court-scheduler/move-booking/12345/from/A1000KT/to/A1234KT")),
      )
    }

    @Test
    fun `should NOT resync each offender`() = runTest {
      verify(courtSchedulerMigrationService, never()).resyncPrisonerCourtMovements("A1000KT")
      verify(courtSchedulerMigrationService, never()).resyncPrisonerCourtMovements("A1234KT")
    }

    @Test
    fun `should publish error telemetry`() {
      verify(telemetryClient).trackEvent(
        eq("court-scheduler-move-booking-error"),
        check {
          assertThat(it["bookingId"]).isEqualTo("12345")
          assertThat(it["fromOffenderNo"]).isEqualTo("A1000KT")
          assertThat(it["toOffenderNo"]).isEqualTo("A1234KT")
          assertThat(it["error"]).isEqualTo("500 Internal Server Error from PUT http://localhost:8106/move/court-appearances")
        },
        isNull(),
      )
    }
  }

  @Nested
  inner class MappingUpdateFailsThenWorksOnRetry {
    private val eventId1 = 123L
    private val dpsScheduleId1 = UUID.randomUUID()

    private val moveBookingMappings = CourtSchedulerMoveBookingMappingDto(
      scheduleIds = listOf(
        CourtScheduleIdMapping(eventId1, dpsScheduleId1),
      ),
      movementIds = listOf(),
    )

    private val nomisData = BookingCourtMovements(
      bookingId = 12345L,
      activeBooking = true,
      latestBooking = true,
      courtSchedules = listOf(
        bookingCourtSchedule(eventId = eventId1, movementOutSeq = null, movementInSeq = null),
      ),
      unscheduledCourtMovementOuts = listOf(),
      unscheduledCourtMovementIns = listOf(),
    )

    @BeforeEach
    fun setUp() = runTest {
      courtSchedulerNomisApi.stubGetBookingCourtMovements(12345L, nomisData)
      mappingApi.stubGetMoveBookingMappings(12345L, moveBookingMappings)
      dpsApi.stubMoveBooking()
      mappingApi.stubMoveBookingMappingsFailureFollowedBySuccess(bookingId = 12345L, fromOffenderNo = "A1000KT", toOffenderNo = "A1234KT")

      doNothing().whenever(courtSchedulerMigrationService).resyncPrisonerCourtMovements(any())

      sendMessage(
        bookingMovedDomainEvent(
          bookingId = 12345,
          movedToNomsNumber = "A1234KT",
          movedFromNomsNumber = "A1000KT",
        ),
      )
        .also { waitForAnyProcessingToComplete("court-scheduler-move-booking-mapping-retry-updated") }
    }

    @Test
    fun `should attempt to move mappings to the new offender no twice`() {
      mappingApi.verify(
        count = 2,
        putRequestedFor(urlEqualTo("/mapping/court-scheduler/move-booking/12345/from/A1000KT/to/A1234KT")),
      )
    }

    @Test
    fun `should resync each offender`() = runTest {
      verify(courtSchedulerMigrationService).resyncPrisonerCourtMovements("A1000KT")
      verify(courtSchedulerMigrationService).resyncPrisonerCourtMovements("A1234KT")
    }

    @Test
    fun `should publish telemetry`() {
      verify(telemetryClient).trackEvent(
        eq("court-scheduler-move-booking-success"),
        check {
          assertThat(it["bookingId"]).isEqualTo("12345")
          assertThat(it["fromOffenderNo"]).isEqualTo("A1000KT")
          assertThat(it["toOffenderNo"]).isEqualTo("A1234KT")
          assertThat(it["nomisEventIds"]).contains("$eventId1")
          assertThat(it["dpsCourtAppearanceIds"]).contains("$dpsScheduleId1")
        },
        isNull(),
      )
    }
  }

  private fun sendMessage(event: String) = awsSqsCourtMovementsOffenderEventsClient.sendMessage(
    courtMovementsQueueOffenderEventsUrl,
    event,
  )
}
