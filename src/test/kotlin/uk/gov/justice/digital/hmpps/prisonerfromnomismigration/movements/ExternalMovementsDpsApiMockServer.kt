package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.stereotype.Component
import org.springframework.test.context.junit.jupiter.SpringExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.ExternalMovementsDpsApiExtension.Companion.dpsExtMovementsServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.NomisAudit
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.SyncResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.TapApplicationRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.OrganisationsDpsApiExtension.Companion.objectMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.getRequestBodies
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.getRequestBody
import java.time.LocalDateTime
import java.util.*

class ExternalMovementsDpsApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val dpsExtMovementsServer = ExternalMovementsDpsApiMockServer()
    lateinit var objectMapper: ObjectMapper

    @Suppress("unused")
    inline fun <reified T> getRequestBody(pattern: RequestPatternBuilder): T = dpsExtMovementsServer.getRequestBody(pattern, objectMapper)

    inline fun <reified T> getRequestBodies(pattern: RequestPatternBuilder): List<T> = dpsExtMovementsServer.getRequestBodies(pattern, objectMapper)
  }

  override fun beforeAll(context: ExtensionContext) {
    dpsExtMovementsServer.start()
    objectMapper = (SpringExtension.getApplicationContext(context).getBean("jacksonObjectMapper") as ObjectMapper)
  }

  override fun beforeEach(context: ExtensionContext) {
    dpsExtMovementsServer.resetAll()
  }

  override fun afterAll(context: ExtensionContext) {
    dpsExtMovementsServer.stop()
  }
}

@Component
class ExternalMovementsDpsApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8103
    private val today = LocalDateTime.now()
    private val tomorrow = today.plusDays(1)

    fun syncTapApplicationRequest() = TapApplicationRequest(
      movementApplicationId = 1234,
      eventSubType = "C5",
      applicationDate = today.toLocalDate(),
      fromDate = today.toLocalDate(),
      toDate = tomorrow.toLocalDate(),
      applicationType = "SINGLE",
      applicationStatus = "ACCEPTED",
      prisonId = "LEI",
      comment = "Application comment",
      contactPersonName = "Dave",
      temporaryAbsenceType = "RDR",
      temporaryAbsenceSubType = "RR",
      audit = NomisAudit(
        createDatetime = today,
        createUsername = "AAA11A",
        modifyDatetime = today,
        modifyUserId = "AAA11A",
        auditTimestamp = today,
        auditUserId = "AAA11A",
      ),

    )

    fun syncResponse() = SyncResponse(UUID.randomUUID())
  }

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

  fun stubSyncTapApplication(personIdentifier: String = "A1234BC", response: SyncResponse = syncResponse()) {
    dpsExtMovementsServer.stubFor(
      put("/sync/temporary-absence-application/$personIdentifier")
        .willReturn(
          aResponse()
            .withStatus(201)
            .withHeader("Content-Type", "application/json")
            .withBody(objectMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubSyncTapApplicationError(
    personIdentifier: String = "A1234BC",
    status: Int = 500,
    error: ErrorResponse = ErrorResponse(status = status),
  ) {
    dpsExtMovementsServer.stubFor(
      put("/sync/temporary-absence-application/$personIdentifier")
        .willReturn(
          aResponse()
            .withStatus(status)
            .withHeader("Content-Type", "application/json")
            .withBody(objectMapper.writeValueAsString(error)),
        ),
    )
  }
}
