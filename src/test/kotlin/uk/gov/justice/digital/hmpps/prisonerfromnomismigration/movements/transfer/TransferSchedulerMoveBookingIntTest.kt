package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.transfer

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
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.http.HttpStatus.NOT_FOUND
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.bookingMovedDomainEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.transfer.TransferScheduleDpsApiExtension.Companion.dpsTransferSchedulerServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.transfer.TransferScheduleDpsApiMockServer.Companion.getRequestBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.transfer.TransferScheduleNomisApiMockServer.Companion.transferMovementOutResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.transfer.TransferScheduleNomisApiMockServer.Companion.transferScheduleOutResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TransferMovementIdMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TransferScheduleIdMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TransferSchedulerMoveBookingMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.BookingTransferMovements
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.BookingTransferSchedule
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.transferschedule.model.MoveTransfersRequest
import java.util.*

class TransferSchedulerMoveBookingIntTest(
  @Autowired private val mappingApi: TransferScheduleMappingApiMockServer,
  @Autowired private val nomisApi: TransferScheduleNomisApiMockServer,
) : TransferSchedulerIntegrationTestBase() {

  private val dpsApi = dpsTransferSchedulerServer

  @Nested
  inner class HappyPath {
    private val eventId = 123L
    private val dpsTransferScheduleId: UUID = UUID.randomUUID()
    private val movementSeq1 = 3
    private val movementSeq2 = 4
    private val dpsMovementId1 = UUID.randomUUID()
    private val dpsMovementId2 = UUID.randomUUID()

    private val nomisTransfers = BookingTransferMovements(
      bookingId = 12345L,
      activeBooking = true,
      latestBooking = true,
      transferSchedules = listOf(
        BookingTransferSchedule(
          schedule = transferScheduleOutResponse(eventId = eventId),
          movement = transferMovementOutResponse().copy(eventId = eventId, sequence = movementSeq1),
        ),
      ),
      unscheduledTransferMovements = listOf(transferMovementOutResponse().copy(transferScheduleOutId = null, sequence = movementSeq2)),
    )

    private val transferMappings = TransferSchedulerMoveBookingMappingDto(
      scheduleIds = listOf(
        TransferScheduleIdMapping(eventId, dpsTransferScheduleId),
      ),
      movementIds = listOf(
        TransferMovementIdMapping(movementSeq1, dpsMovementId1),
        TransferMovementIdMapping(movementSeq2, dpsMovementId2),
      ),
    )

    @BeforeEach
    fun setUp() = runTest {
      nomisApi.stubGetBookingTransferMovements(bookingId = 12345L, nomisTransfers)
      mappingApi.stubGetMoveBookingMappings(bookingId = 12345L, transferMappings)
      dpsApi.stubMoveBooking()
      mappingApi.stubMoveBookingMappings(bookingId = 12345L, fromOffenderNo = "A1000KT", toOffenderNo = "A1234KT")
      doNothing().whenever(transferSchedulerMigrationService).resyncPrisonerTransferMovements(any())

      sendMessage(
        bookingMovedDomainEvent(
          bookingId = 12345L,
          movedToNomsNumber = "A1234KT",
          movedFromNomsNumber = "A1000KT",
        ),
      )
        .also { waitForAnyProcessingToComplete() }
    }

    @Test
    fun `should get booking transfers from NOMIS`() {
      nomisApi.verifyGetBookingTransferMovements(bookingId = 12345L)
    }

    @Test
    fun `should get mappings`() {
      mappingApi.verify(getRequestedFor(urlEqualTo("/mapping/transfer-scheduler/move-booking/12345")))
    }

    @Test
    fun `should move DPS IDs`() {
      getRequestBody<MoveTransfersRequest>(
        putRequestedFor(urlEqualTo("/move/transfers")),
      ).apply {
        assertThat(from).isEqualTo("A1000KT")
        assertThat(to).isEqualTo("A1234KT")
        assertThat(transferIds).containsExactlyInAnyOrder(dpsTransferScheduleId)
      }
    }

    @Test
    fun `should move mappings to the new offender no`() {
      mappingApi.verify(putRequestedFor(urlEqualTo("/mapping/transfer-scheduler/move-booking/12345/from/A1000KT/to/A1234KT")))
    }

    @Test
    fun `should resync each offender`() = runTest {
      verify(transferSchedulerMigrationService).resyncPrisonerTransferMovements("A1000KT")
      verify(transferSchedulerMigrationService).resyncPrisonerTransferMovements("A1234KT")
    }

    @Test
    fun `should publish telemetry`() {
      verify(telemetryClient).trackEvent(
        eq("transfer-scheduler-move-booking-success"),
        check {
          assertThat(it["bookingId"]).isEqualTo("12345")
          assertThat(it["fromOffenderNo"]).isEqualTo("A1000KT")
          assertThat(it["toOffenderNo"]).isEqualTo("A1234KT")
          assertThat(it["nomisEventIds"]).contains("$eventId")
          assertThat(it["dpsTransferIds"]).contains("$dpsTransferScheduleId")
        },
        isNull(),
      )
    }
  }

  @Nested
  inner class PrisonerNotFoundInNomis {
    @BeforeEach
    fun setUp() = runTest {
      nomisApi.stubGetBookingTransferMovements(status = NOT_FOUND)

      sendMessage(
        bookingMovedDomainEvent(
          bookingId = 12345L,
          movedToNomsNumber = "A1234KT",
          movedFromNomsNumber = "A1000KT",
        ),
      )
        .also { waitForAnyProcessingToComplete("transfer-scheduler-move-booking-ignored") }
    }

    @Test
    fun `should get booking transfers from NOMIS`() {
      nomisApi.verifyGetBookingTransferMovements(bookingId = 12345L)
    }

    @Test
    fun `should NOT move DPS IDs`() {
      dpsApi.verify(
        0,
        putRequestedFor(urlEqualTo("/move/transfers")),
      )
    }

    @Test
    fun `should NOT move mappings to the new offender no`() {
      mappingApi.verify(
        count = 0,
        putRequestedFor(urlEqualTo("/mapping/transfer-scheduler/move-booking/12345/from/A1000KT/to/A1234KT")),
      )
    }

    @Test
    fun `should resync any offenders`() = runTest {
      verify(transferSchedulerMigrationService, times(0)).resyncPrisonerTransferMovements("A1000KT")
    }

    @Test
    fun `should publish ignore telemetry`() {
      verify(telemetryClient).trackEvent(
        eq("transfer-scheduler-move-booking-ignored"),
        check {
          assertThat(it["bookingId"]).isEqualTo("12345")
          assertThat(it["fromOffenderNo"]).isEqualTo("A1000KT")
          assertThat(it["toOffenderNo"]).isEqualTo("A1234KT")
          assertThat(it["reason"]).contains("No transfers found for booking=12345")
        },
        isNull(),
      )
    }
  }

  @Nested
  inner class NoTransfersToMove {
    @BeforeEach
    fun setUp() = runTest {
      nomisApi.stubGetBookingTransferMovements(
        bookingId = 12345L,
        response = BookingTransferMovements(
          bookingId = 12345L,
          activeBooking = true,
          latestBooking = true,
          transferSchedules = listOf(),
          unscheduledTransferMovements = listOf(),
        ),
      )

      sendMessage(
        bookingMovedDomainEvent(
          bookingId = 12345L,
          movedToNomsNumber = "A1234KT",
          movedFromNomsNumber = "A1000KT",
        ),
      )
        .also { waitForAnyProcessingToComplete("transfer-scheduler-move-booking-ignored") }
    }

    @Test
    fun `should get booking transfers from NOMIS`() {
      nomisApi.verifyGetBookingTransferMovements(bookingId = 12345L)
    }

    @Test
    fun `should NOT move DPS IDs`() {
      dpsApi.verify(
        0,
        putRequestedFor(urlEqualTo("/move/transfers")),
      )
    }

    @Test
    fun `should NOT move mappings to the new offender no`() {
      mappingApi.verify(
        count = 0,
        putRequestedFor(urlEqualTo("/mapping/transfer-scheduler/move-booking/12345/from/A1000KT/to/A1234KT")),
      )
    }

    @Test
    fun `should NOT resync any offenders`() = runTest {
      verify(transferSchedulerMigrationService, times(0)).resyncPrisonerTransferMovements("A1000KT")
    }

    @Test
    fun `should publish ignore telemetry`() {
      verify(telemetryClient).trackEvent(
        eq("transfer-scheduler-move-booking-ignored"),
        check {
          assertThat(it["bookingId"]).isEqualTo("12345")
          assertThat(it["fromOffenderNo"]).isEqualTo("A1000KT")
          assertThat(it["toOffenderNo"]).isEqualTo("A1234KT")
          assertThat(it["reason"]).contains("No transfers found for booking=12345")
        },
        isNull(),
      )
    }
  }

  @Nested
  inner class DpsUpdateFails {
    private val eventId = 123L
    private val dpsTransferScheduleId: UUID = UUID.randomUUID()

    private val nomisTransfers = BookingTransferMovements(
      bookingId = 12345L,
      activeBooking = true,
      latestBooking = true,
      transferSchedules = listOf(
        BookingTransferSchedule(
          schedule = transferScheduleOutResponse(eventId = eventId),
        ),
      ),
      unscheduledTransferMovements = listOf(),
    )

    private val transferMappings = TransferSchedulerMoveBookingMappingDto(
      scheduleIds = listOf(
        TransferScheduleIdMapping(eventId, dpsTransferScheduleId),
      ),
      movementIds = listOf(),
    )

    @BeforeEach
    fun setUp() = runTest {
      nomisApi.stubGetBookingTransferMovements(bookingId = 12345L, nomisTransfers)
      mappingApi.stubGetMoveBookingMappings(bookingId = 12345L, transferMappings)
      dpsApi.stubMoveBookingError(status = BAD_REQUEST.value())

      sendMessage(
        bookingMovedDomainEvent(
          bookingId = 12345L,
          movedToNomsNumber = "A1234KT",
          movedFromNomsNumber = "A1000KT",
        ),
      )
        .also { waitForAnyProcessingToComplete("transfer-scheduler-move-booking-error", times = 2) }
    }

    @Test
    fun `should get booking transfers from NOMIS`() {
      nomisApi.verifyGetBookingTransferMovements(bookingId = 12345L, count = 2)
    }

    @Test
    fun `should attempt to move DPS IDs`() {
      dpsApi.verify(putRequestedFor(urlEqualTo("/move/transfers")))
    }

    @Test
    fun `should NOT move mappings to the new offender no`() {
      mappingApi.verify(
        count = 0,
        putRequestedFor(urlEqualTo("/mapping/transfer-scheduler/move-booking/12345/from/A1000KT/to/A1234KT")),
      )
    }

    @Test
    fun `should NOT resync any offenders`() = runTest {
      verify(transferSchedulerMigrationService, never()).resyncPrisonerTransferMovements("A1000KT")
    }

    @Test
    fun `should publish telemetry`() {
      verify(telemetryClient, times(2)).trackEvent(
        eq("transfer-scheduler-move-booking-error"),
        check {
          assertThat(it["bookingId"]).isEqualTo("12345")
          assertThat(it["fromOffenderNo"]).isEqualTo("A1000KT")
          assertThat(it["toOffenderNo"]).isEqualTo("A1234KT")
          assertThat(it["nomisEventIds"]).contains("$eventId")
          assertThat(it["dpsTransferIds"]).contains("$dpsTransferScheduleId")
          assertThat(it["error"]).isEqualTo("400 Bad Request from PUT http://localhost:8108/move/transfers")
        },
        isNull(),
      )
    }
  }

  @Nested
  inner class MappingFailureRetry {
    private val eventId = 123L
    private val dpsTransferScheduleId: UUID = UUID.randomUUID()

    private val nomisTransfers = BookingTransferMovements(
      bookingId = 12345L,
      activeBooking = true,
      latestBooking = true,
      transferSchedules = listOf(
        BookingTransferSchedule(
          schedule = transferScheduleOutResponse(eventId = eventId),
        ),
      ),
      unscheduledTransferMovements = listOf(),
    )

    private val transferMappings = TransferSchedulerMoveBookingMappingDto(
      scheduleIds = listOf(
        TransferScheduleIdMapping(eventId, dpsTransferScheduleId),
      ),
      movementIds = listOf(),
    )

    @BeforeEach
    fun setUp() = runTest {
      nomisApi.stubGetBookingTransferMovements(bookingId = 12345L, nomisTransfers)
      mappingApi.stubGetMoveBookingMappings(bookingId = 12345L, transferMappings)
      dpsApi.stubMoveBooking()
      mappingApi.stubMoveBookingMappingsFailureFollowedBySuccess(bookingId = 12345L, fromOffenderNo = "A1000KT", toOffenderNo = "A1234KT")
      doNothing().whenever(transferSchedulerMigrationService).resyncPrisonerTransferMovements(any())

      sendMessage(
        bookingMovedDomainEvent(
          bookingId = 12345L,
          movedToNomsNumber = "A1234KT",
          movedFromNomsNumber = "A1000KT",
        ),
      )
        .also { waitForAnyProcessingToComplete("transfer-scheduler-move-booking-mapping-retry-updated") }
    }

    @Test
    fun `should move DPS IDs once`() {
      getRequestBody<MoveTransfersRequest>(
        putRequestedFor(urlEqualTo("/move/transfers")),
      ).apply {
        assertThat(from).isEqualTo("A1000KT")
        assertThat(to).isEqualTo("A1234KT")
        assertThat(transferIds).containsExactlyInAnyOrder(dpsTransferScheduleId)
      }
    }

    @Test
    fun `should attempt to move mappings to the new offender twice`() {
      mappingApi.verify(
        count = 2,
        putRequestedFor(urlEqualTo("/mapping/transfer-scheduler/move-booking/12345/from/A1000KT/to/A1234KT")),
      )
    }

    @Test
    fun `should resync each offender`() = runTest {
      verify(transferSchedulerMigrationService).resyncPrisonerTransferMovements("A1000KT")
      verify(transferSchedulerMigrationService).resyncPrisonerTransferMovements("A1234KT")
    }

    @Test
    fun `should publish telemetry`() {
      verify(telemetryClient).trackEvent(
        eq("transfer-scheduler-move-booking-success"),
        check {
          assertThat(it["bookingId"]).isEqualTo("12345")
          assertThat(it["fromOffenderNo"]).isEqualTo("A1000KT")
          assertThat(it["toOffenderNo"]).isEqualTo("A1234KT")
          assertThat(it["nomisEventIds"]).contains("$eventId")
          assertThat(it["dpsTransferIds"]).contains("$dpsTransferScheduleId")
        },
        isNull(),
      )
    }
  }

  private fun sendMessage(event: String) = awsSqsTransferMovementsOffenderEventsClient.sendMessage(
    transferMovementsQueueOffenderEventsUrl,
    event,
  )
}
