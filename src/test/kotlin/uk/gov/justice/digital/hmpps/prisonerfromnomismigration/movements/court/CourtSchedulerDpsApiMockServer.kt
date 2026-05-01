package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.court

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtscheduler.model.ReferenceId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtscheduler.model.SyncCourtEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtscheduler.model.SyncUser
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.court.CourtSchedulerDpsApiExtension.Companion.dpsCourtSchedulerServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.court.CourtSchedulerDpsApiExtension.Companion.jsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.OrganisationsDpsApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.getRequestBodies
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.getRequestBody
import java.time.LocalDate
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
        eventDate = LocalDate.now(),
        startTime = "${LocalDateTime.now()}",
        courtEventType = "CRT",
        eventStatus = "SCH",
        commentText = "court schedule out comment",
        externalReferenceUrn = "Some ext ref URN",
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
  }

  fun stubSyncCourtEvent(personIdentifier: String, response: ReferenceId = referenceId()) {
    dpsCourtSchedulerServer.stubFor(
      put("/sync/court-appearances/$personIdentifier")
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(OrganisationsDpsApiExtension.jsonMapper.writeValueAsString(response)),
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
}
