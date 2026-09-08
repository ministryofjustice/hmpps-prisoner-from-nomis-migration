package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.transfer

import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
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
  }

  private fun sendMessage(event: String) = awsSqsTransferMovementsOffenderEventsClient.sendMessage(
    transferMovementsQueueOffenderEventsUrl,
    event,
  )
}
