package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.court

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.delete
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.stereotype.Component
import org.springframework.test.context.junit.jupiter.SpringExtension.getApplicationContext
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtscheduler.model.CourtEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtscheduler.model.CourtEventMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtscheduler.model.CourtEventMovement
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtscheduler.model.CourtMovementMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtscheduler.model.MoveCourtEventRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtscheduler.model.ReferenceId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtscheduler.model.ResyncResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtscheduler.model.SyncCourtEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtscheduler.model.SyncCourtEventMovement
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtscheduler.model.SyncUser
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.court.CourtSchedulerDpsApiExtension.Companion.dpsCourtSchedulerServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.court.CourtSchedulerDpsApiExtension.Companion.jsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.getRequestBodies
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.getRequestBody
import java.time.LocalDateTime
import java.util.*

class CourtSchedulerDpsApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    private var enableResetBeforeEach = true

    @JvmField
    val dpsCourtSchedulerServer = CourtSchedulerDpsApiMockServer()
    lateinit var jsonMapper: JsonMapper

    fun resetAndDisableResetBeforeEach() {
      enableResetBeforeEach = false
      dpsCourtSchedulerServer.resetAll()
    }
  }

  override fun beforeAll(context: ExtensionContext) {
    dpsCourtSchedulerServer.start()
    jsonMapper = (getApplicationContext(context).getBean("jacksonJsonMapper") as JsonMapper)
  }

  override fun beforeEach(context: ExtensionContext) {
    if (enableResetBeforeEach) dpsCourtSchedulerServer.resetAll()
  }

  override fun afterAll(context: ExtensionContext) {
    dpsCourtSchedulerServer.stop()
    enableResetBeforeEach = true
  }
}

@Component
class CourtSchedulerDpsApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8106

    @Suppress("unused")
    inline fun <reified T> getRequestBody(pattern: RequestPatternBuilder): T = dpsCourtSchedulerServer.getRequestBody(pattern, jsonMapper)
    inline fun <reified T> getRequestBodies(pattern: RequestPatternBuilder): List<T> = dpsCourtSchedulerServer.getRequestBodies(pattern, jsonMapper)

    fun syncCourtEvent(id: UUID? = null) = SyncCourtEvent(
      occurredAt = LocalDateTime.now(),
      user = SyncUser(
        username = "USER",
        activeCaseloadId = "MDI",
      ),
      courtEvent = CourtEvent(
        dpsId = id ?: UUID.randomUUID(),
        prisonCodeAtTimeOfScheduling = "MDI",
        agyLocId = "LEEDMC",
        eventId = 1L,
        start = LocalDateTime.now(),
        courtEventType = "CRT",
        eventStatus = "SCH",
        commentText = "court schedule out comment",
        externalReferenceUrn = "Some ext ref URN",
      ),
    )

    fun syncCourtMovement(
      id: UUID? = null,
      scheduleId: UUID? = null,
    ) = SyncCourtEventMovement(
      occurredAt = LocalDateTime.now(),
      user = SyncUser(
        username = "USER",
        activeCaseloadId = "MDI",
      ),
      movement = CourtEventMovement(
        dpsId = id ?: UUID.randomUUID(),
        dpsCourtAppearanceScheduleId = scheduleId,
        offenderBookId = 12345L,
        movementSeq = 3,
        occurredAt = LocalDateTime.now(),
        movementReasonCode = "CRT",
        directionCode = "OUT",
        fromAgencyId = "BXI",
        toAgencyId = "LEEDMC",
      ),
    )

    fun referenceId(id: UUID = UUID.randomUUID()) = ReferenceId(id)

    fun stubHealthPing(status: Int) {
      stubFor(
        get("/health/ping").willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(if (status == 200) "pong" else "some error")
            .withStatus(status),
        ),
      )
    }

    fun resyncResponse(
      dpsCourtAppearanceId: UUID = UUID.randomUUID(),
      dpsScheduledMovementOutId: UUID = UUID.randomUUID(),
      dpsScheduledMovementInId: UUID = UUID.randomUUID(),
      dpsUnscheduledMovementOutId: UUID = UUID.randomUUID(),
      dpsUnscheduledMovementInId: UUID = UUID.randomUUID(),
    ) = ResyncResponse(
      courtEvents = listOf(
        CourtEventMapping(
          dpsId = dpsCourtAppearanceId,
          eventId = 1L,
          movements = listOf(
            CourtMovementMapping(
              dpsId = dpsScheduledMovementOutId,
              offenderBookId = 12345L,
              movementSeq = 3,
            ),
            CourtMovementMapping(
              dpsId = dpsScheduledMovementInId,
              offenderBookId = 12345L,
              movementSeq = 4,
            ),
          ),
        ),
      ),
      unscheduledMovements = listOf(
        CourtMovementMapping(
          dpsId = dpsUnscheduledMovementOutId,
          offenderBookId = 12345L,
          movementSeq = 1,
        ),
        CourtMovementMapping(
          dpsId = dpsUnscheduledMovementInId,
          offenderBookId = 12345L,
          movementSeq = 2,
        ),
      ),
    )

    fun moveBookingRequest(
      fromPrisoner: String = "A1234BC",
      toPrisoner: String = "A1234BD",
      scheduleIds: List<UUID> = listOf(UUID.randomUUID()),
      unscheduledMovementIds: List<UUID> = listOf(UUID.randomUUID()),
    ) = MoveCourtEventRequest(
      fromPersonIdentifier = fromPrisoner,
      toPersonIdentifier = toPrisoner,
      scheduleIds = scheduleIds.toSet(),
      unscheduledMovementIds = unscheduledMovementIds.toSet(),
    )
  }

  fun stubSyncCourtEvent(personIdentifier: String, response: ReferenceId = referenceId()) {
    dpsCourtSchedulerServer.stubFor(
      put("/sync/court-appearances/$personIdentifier")
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(jsonMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubSyncCourtEventError(
    personIdentifier: String,
    status: Int = 500,
    error: ErrorResponse = ErrorResponse(status = status),
  ) {
    dpsCourtSchedulerServer.stubFor(
      put("/sync/court-appearances/$personIdentifier")
        .willReturn(
          aResponse()
            .withStatus(status)
            .withHeader("Content-Type", "application/json")
            .withBody(jsonMapper.writeValueAsString(error)),
        ),
    )
  }

  fun stubDeleteCourtEvent(courtAppearanceId: UUID) {
    dpsCourtSchedulerServer.stubFor(
      delete("/sync/court-appearances/$courtAppearanceId")
        .willReturn(
          aResponse()
            .withStatus(204),
        ),
    )
  }

  fun stubDeleteCourtEventError(
    courtAppearanceId: UUID,
    status: Int = 500,
    error: ErrorResponse = ErrorResponse(status = status),
  ) {
    dpsCourtSchedulerServer.stubFor(
      delete("/sync/court-appearances/$courtAppearanceId")
        .willReturn(
          aResponse()
            .withStatus(status)
            .withHeader("Content-Type", "application/json")
            .withBody(jsonMapper.writeValueAsString(error)),
        ),
    )
  }

  fun stubSyncCourtMovement(personIdentifier: String, response: ReferenceId = referenceId()) {
    dpsCourtSchedulerServer.stubFor(
      put("/sync/court-appearance-movements/$personIdentifier")
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(jsonMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubSyncCourtMovementError(
    personIdentifier: String,
    status: Int = 500,
    error: ErrorResponse = ErrorResponse(status = status),
  ) {
    dpsCourtSchedulerServer.stubFor(
      put("/sync/court-appearance-movements/$personIdentifier")
        .willReturn(
          aResponse()
            .withStatus(status)
            .withHeader("Content-Type", "application/json")
            .withBody(jsonMapper.writeValueAsString(error)),
        ),
    )
  }

  fun stubDeleteCourtMovement(courtMovementId: UUID) {
    dpsCourtSchedulerServer.stubFor(
      delete("/sync/court-appearance-movements/$courtMovementId")
        .willReturn(
          aResponse()
            .withStatus(204),
        ),
    )
  }

  fun stubDeleteCourtMovementError(
    courtMovementId: UUID,
    status: Int = 500,
    error: ErrorResponse = ErrorResponse(status = status),
  ) {
    dpsCourtSchedulerServer.stubFor(
      delete("/sync/court-appearance-movements/$courtMovementId")
        .willReturn(
          aResponse()
            .withStatus(status)
            .withHeader("Content-Type", "application/json")
            .withBody(jsonMapper.writeValueAsString(error)),
        ),
    )
  }

  fun stubResyncPrisonerCourtAppearances(personIdentifier: String = "A1234BC", response: ResyncResponse = resyncResponse()) {
    dpsCourtSchedulerServer.stubFor(
      put("/resync/court-appearances/$personIdentifier")
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(jsonMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubResyncPrisonerCourtAppearances(
    personIdentifier: String = "A1234BC",
    status: Int = 500,
    error: ErrorResponse = ErrorResponse(status = status),
  ) {
    dpsCourtSchedulerServer.stubFor(
      put("/resync/court-appearances/$personIdentifier")
        .willReturn(
          aResponse()
            .withStatus(status)
            .withHeader("Content-Type", "application/json")
            .withBody(jsonMapper.writeValueAsString(error)),
        ),
    )
  }

  fun stubMoveBooking() {
    dpsCourtSchedulerServer.stubFor(
      put("/move/court-appearances")
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json"),
        ),
    )
  }

  fun stubMoveBookingError(
    status: Int = 500,
    error: ErrorResponse = ErrorResponse(status = status),
  ) {
    dpsCourtSchedulerServer.stubFor(
      put("/move/court-appearances")
        .willReturn(
          aResponse()
            .withStatus(status)
            .withHeader("Content-Type", "application/json")
            .withBody(jsonMapper.writeValueAsString(error)),
        ),
    )
  }
}
