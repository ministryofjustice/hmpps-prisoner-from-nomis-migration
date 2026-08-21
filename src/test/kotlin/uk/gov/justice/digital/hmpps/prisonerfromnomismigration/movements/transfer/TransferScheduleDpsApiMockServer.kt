package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.transfer

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.delete
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.stereotype.Component
import org.springframework.test.context.junit.jupiter.SpringExtension.getApplicationContext
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.transfer.TransferScheduleDpsApiExtension.Companion.dpsTransferSchedulerServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.transfer.TransferScheduleDpsApiExtension.Companion.jsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.transferschedule.model.ReferenceId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.transferschedule.model.ResyncResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.transferschedule.model.SyncMovement
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.transferschedule.model.SyncMovementRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.transferschedule.model.SyncSchedule
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.transferschedule.model.SyncTransfer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.transferschedule.model.SyncTransferRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.transferschedule.model.SyncUser
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.transferschedule.model.TransferMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.transferschedule.model.TransferMovementMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.getRequestBodies
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.getRequestBody
import java.time.LocalDateTime
import java.util.*

class TransferScheduleDpsApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    private var enableResetBeforeEach = true

    @JvmField
    val dpsTransferSchedulerServer = TransferScheduleDpsApiMockServer()
    lateinit var jsonMapper: JsonMapper

    fun resetAndDisableResetBeforeEach() {
      enableResetBeforeEach = false
      dpsTransferSchedulerServer.resetAll()
    }
  }

  override fun beforeAll(context: ExtensionContext) {
    dpsTransferSchedulerServer.start()
    jsonMapper = (getApplicationContext(context).getBean("jacksonJsonMapper") as JsonMapper)
  }

  override fun beforeEach(context: ExtensionContext) {
    if (enableResetBeforeEach) dpsTransferSchedulerServer.resetAll()
  }

  override fun afterAll(context: ExtensionContext) {
    dpsTransferSchedulerServer.stop()
    enableResetBeforeEach = true
  }
}

@Component
class TransferScheduleDpsApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8108

    @Suppress("unused")
    inline fun <reified T> getRequestBody(pattern: RequestPatternBuilder): T = dpsTransferSchedulerServer.getRequestBody(pattern, jsonMapper)
    inline fun <reified T> getRequestBodies(pattern: RequestPatternBuilder): List<T> = dpsTransferSchedulerServer.getRequestBodies(pattern, jsonMapper)

    fun syncTransferRequest(
      dpsId: UUID? = null,
      eventId: Long? = 1L,
    ) = SyncTransferRequest(
      occurredAt = LocalDateTime.now(),
      syncUser = SyncUser(
        username = "USER",
        activeCaseloadId = "MDI",
      ),
      transfer = SyncTransfer(
        dpsId = dpsId,
        eventId = eventId,
        schedule = SyncSchedule(
          start = LocalDateTime.now(),
          eventSubType = "TRN",
          eventStatus = "SCH",
          agyLocId = "BXI",
          toAgyLocId = "LEI",
        ),
      ),
    )

    fun syncTransferMovementRequest(
      dpsId: UUID? = null,
      dpsTransferId: UUID? = null,
      nomisBookingId: Long? = 12345L,
      nomisMovementSeq: Int? = 3,
    ) = SyncMovementRequest(
      occurredAt = LocalDateTime.now(),
      syncUser = SyncUser(
        username = "USER",
        activeCaseloadId = "MDI",
      ),
      movement = SyncMovement(
        dpsId = dpsId,
        dpsTransferId = dpsTransferId,
        offenderBookId = nomisBookingId,
        movementSeq = nomisMovementSeq,
        occurredAt = LocalDateTime.now(),
        movementReasonCode = "28",
        escortCode = "PECS",
        fromAgyLocId = "BXI",
        toAgyLocId = "LEI",
        active = true,
        commentText = "some transfer movement comment",
      ),
    )

    fun referenceId(id: UUID = UUID.randomUUID()) = ReferenceId(id)

    fun resyncResponse(
      dpsTransferId: UUID = UUID.randomUUID(),
      nomisEventId: Long = 1,
      dpsScheduledMovementId: UUID = UUID.randomUUID(),
      nomisMovementSeq: Int = 3,
      dpsUnscheduledMovementId: UUID = UUID.randomUUID(),
      nomisUnscheduledMovementSeq: Int = 1,
    ) = ResyncResponse(
      transfers = listOf(
        TransferMapping(
          dpsId = dpsTransferId,
          eventId = nomisEventId,
          movement =
          TransferMovementMapping(
            dpsId = dpsScheduledMovementId,
            offenderBookId = 12345L,
            movementSeq = nomisMovementSeq,
          ),
        ),
      ),
      unscheduledMovements = listOf(
        TransferMovementMapping(
          dpsId = dpsUnscheduledMovementId,
          offenderBookId = 12345L,
          movementSeq = nomisUnscheduledMovementSeq,
        ),
      ),
    )

    fun stubHealthPing(status: Int) {
      dpsTransferSchedulerServer.stubFor(
        get("/health/ping").willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(if (status == 200) "pong" else "some error")
            .withStatus(status),
        ),
      )
    }
  }

  fun stubSyncTransferSchedule(personIdentifier: String, response: ReferenceId = referenceId()) {
    dpsTransferSchedulerServer.stubFor(
      put("/sync/transfers/$personIdentifier")
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(jsonMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubSyncTransferScheduleError(
    personIdentifier: String,
    status: Int = 500,
    error: ErrorResponse = ErrorResponse(status = status),
  ) {
    dpsTransferSchedulerServer.stubFor(
      put("/sync/transfers/$personIdentifier")
        .willReturn(
          aResponse()
            .withStatus(status)
            .withHeader("Content-Type", "application/json")
            .withBody(jsonMapper.writeValueAsString(error)),
        ),
    )
  }

  fun stubDeleteTransferSchedule(dpsId: UUID) {
    dpsTransferSchedulerServer.stubFor(
      delete("/sync/transfers/$dpsId")
        .willReturn(
          aResponse()
            .withStatus(204),
        ),
    )
  }

  fun stubDeleteTransferScheduleError(
    dpsId: UUID,
    status: Int = 500,
    error: ErrorResponse = ErrorResponse(status = status),
  ) {
    dpsTransferSchedulerServer.stubFor(
      delete("/sync/transfers/$dpsId")
        .willReturn(
          aResponse()
            .withStatus(status)
            .withHeader("Content-Type", "application/json")
            .withBody(jsonMapper.writeValueAsString(error)),
        ),
    )
  }

  fun stubSyncTransferMovement(personIdentifier: String, response: ReferenceId = referenceId()) {
    dpsTransferSchedulerServer.stubFor(
      put("/sync/transfer-movements/$personIdentifier")
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(jsonMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubSyncTransferMovementError(
    personIdentifier: String,
    status: Int = 500,
    error: ErrorResponse = ErrorResponse(status = status),
  ) {
    dpsTransferSchedulerServer.stubFor(
      put("/sync/transfer-movements/$personIdentifier")
        .willReturn(
          aResponse()
            .withStatus(status)
            .withHeader("Content-Type", "application/json")
            .withBody(jsonMapper.writeValueAsString(error)),
        ),
    )
  }

  fun stubDeleteTransferMovement(dpsId: UUID) {
    dpsTransferSchedulerServer.stubFor(
      delete("/sync/transfer-movements/$dpsId")
        .willReturn(
          aResponse()
            .withStatus(204),
        ),
    )
  }

  fun stubDeleteTransferMovementError(
    dpsId: UUID,
    status: Int = 500,
    error: ErrorResponse = ErrorResponse(status = status),
  ) {
    dpsTransferSchedulerServer.stubFor(
      delete("/sync/transfer-movements/$dpsId")
        .willReturn(
          aResponse()
            .withStatus(status)
            .withHeader("Content-Type", "application/json")
            .withBody(jsonMapper.writeValueAsString(error)),
        ),
    )
  }

  fun stubResyncPrisonerTransfers(personIdentifier: String = "A1234BC", response: ResyncResponse = resyncResponse()) {
    dpsTransferSchedulerServer.stubFor(
      put("/resync/transfers/$personIdentifier")
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(jsonMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubResyncPrisonerTransfers(
    personIdentifier: String = "A1234BC",
    status: Int = 500,
    error: ErrorResponse = ErrorResponse(status = status),
  ) {
    dpsTransferSchedulerServer.stubFor(
      put("/resync/transfers/$personIdentifier")
        .willReturn(
          aResponse()
            .withStatus(status)
            .withHeader("Content-Type", "application/json")
            .withBody(jsonMapper.writeValueAsString(error)),
        ),
    )
  }
}
