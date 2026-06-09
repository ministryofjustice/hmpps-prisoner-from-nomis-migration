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
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
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
    private val eventId1 = 1234L
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
  }

  private fun sendMessage(event: String) = awsSqsCourtMovementsOffenderEventsClient.sendMessage(
    courtMovementsQueueOffenderEventsUrl,
    event,
  )
}
