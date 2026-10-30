package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.personlocation

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
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.test.context.junit.jupiter.SpringExtension.getApplicationContext
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.personlocation.PersonLocationDpsApiExtension.Companion.dpsPersonLocationServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.personlocation.PersonLocationDpsApiExtension.Companion.jsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personlocation.model.CodedReason
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personlocation.model.CustodialSeriesMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personlocation.model.ExternalMovementMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personlocation.model.Prison
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personlocation.model.ReferenceId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personlocation.model.ResyncExternalMovementsRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personlocation.model.ResyncResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personlocation.model.SyncCustodialSeries
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personlocation.model.SyncCustodialSeriesRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personlocation.model.SyncExternalJourney
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personlocation.model.SyncExternalMovement
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personlocation.model.SyncExternalMovementRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personlocation.model.SyncUser
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.getRequestBodies
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.getRequestBody
import java.time.LocalDateTime
import java.util.UUID

class PersonLocationDpsApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    private var enableResetBeforeEach = true

    @JvmField
    val dpsPersonLocationServer = PersonLocationDpsApiMockServer()
    lateinit var jsonMapper: JsonMapper

    fun resetAndDisableResetBeforeEach() {
      enableResetBeforeEach = false
      dpsPersonLocationServer.resetAll()
    }
  }

  override fun beforeAll(context: ExtensionContext) {
    dpsPersonLocationServer.start()
    jsonMapper = (getApplicationContext(context).getBean("jacksonJsonMapper") as JsonMapper)
  }

  override fun beforeEach(context: ExtensionContext) {
    if (enableResetBeforeEach) dpsPersonLocationServer.resetAll()
  }

  override fun afterAll(context: ExtensionContext) {
    dpsPersonLocationServer.stop()
    enableResetBeforeEach = true
  }
}

@Component
class PersonLocationDpsApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8111

    @Suppress("unused")
    inline fun <reified T> getRequestBody(pattern: RequestPatternBuilder): T = dpsPersonLocationServer.getRequestBody(pattern, jsonMapper)
    inline fun <reified T> getRequestBodies(pattern: RequestPatternBuilder): List<T> = dpsPersonLocationServer.getRequestBodies(pattern, jsonMapper)

    fun resyncExternalMovementsRequest() = ResyncExternalMovementsRequest(
      custodialSeries = listOf(),
    )

    fun syncExternalMovementRequest() = SyncExternalMovementRequest(
      occurredAt = LocalDateTime.parse("2025-09-02T13:23:00"),
      syncUser = SyncUser(username = "USER", activeCaseloadId = "MDI"),
      movement = SyncExternalMovement(
        type = SyncExternalMovement.Type.DEPARTURE,
        reason = CodedReason(description = "Normal Transfer", reasonType = "CodedReason", code = "NOTR"),
        occurredAt = LocalDateTime.parse("2025-09-02T13:23:00"),
        from = Prison(description = "Brixton (HMP)", locationType = "Prison", code = "BXI"),
        to = Prison(description = "Leeds (HMP)", locationType = "Prison", code = "LEI"),
        journey = SyncExternalJourney(type = SyncExternalJourney.Type.TRANSFER),
        legacyBookingId = 12345,
        legacySequenceNumber = 3,
      ),
    )

    fun syncCustodialSeriesRequest() = SyncCustodialSeriesRequest(
      occurredAt = LocalDateTime.parse("2025-09-02T13:23:00"),
      syncUser = SyncUser(username = "USER", activeCaseloadId = "MDI"),
      custodialSeries = SyncCustodialSeries(
        status = SyncCustodialSeries.Status.OPEN,
        isActive = true,
        openedAt = LocalDateTime.parse("2025-09-01T10:00:00"),
        legacyBookingId = 12345,
        legacyBookingReference = "B12345",
      ),
    )

    fun resyncResponse(
      bookingId: Long = 12345L,
      dpsCustodialSeriesId: UUID = UUID.randomUUID(),
      movements: List<Pair<Int, UUID>> = listOf(1 to UUID.randomUUID(), 2 to UUID.randomUUID()),
    ) = ResyncResponse(
      custodialSeries = listOf(
        CustodialSeriesMapping(
          dpsId = dpsCustodialSeriesId,
          legacyBookingId = bookingId,
          movements = movements.map { (sequence, dpsId) -> ExternalMovementMapping(dpsId = dpsId, legacySequenceNumber = sequence) },
        ),
      ),
    )
  }

  fun stubResyncPrisoner(
    personIdentifier: String = "A1234BC",
    response: ResyncResponse = resyncResponse(),
    status: HttpStatus = HttpStatus.CREATED,
    error: ErrorResponse = ErrorResponse(status = status.value()),
  ) {
    dpsPersonLocationServer.stubFor(
      put("/resync/external-movements/$personIdentifier")
        .willReturn(
          aResponse()
            .withStatus(status.value())
            .withHeader("Content-Type", "application/json")
            .withBody(jsonMapper.writeValueAsString(if (status == HttpStatus.CREATED) response else error)),
        ),
    )
  }

  fun stubSyncExternalMovement(
    personIdentifier: String = "A1234BC",
    response: ReferenceId = ReferenceId(dpsId = UUID.randomUUID()),
    status: HttpStatus = HttpStatus.OK,
    error: ErrorResponse = ErrorResponse(status = status.value()),
  ) {
    dpsPersonLocationServer.stubFor(
      put("/sync/external-movements/$personIdentifier")
        .willReturn(
          aResponse()
            .withStatus(status.value())
            .withHeader("Content-Type", "application/json")
            .withBody(jsonMapper.writeValueAsString(if (status == HttpStatus.OK) response else error)),
        ),
    )
  }

  fun stubSyncCustodialSeries(
    personIdentifier: String = "A1234BC",
    response: ReferenceId = ReferenceId(dpsId = UUID.randomUUID()),
    status: HttpStatus = HttpStatus.OK,
    error: ErrorResponse = ErrorResponse(status = status.value()),
  ) {
    dpsPersonLocationServer.stubFor(
      put("/sync/custodial-series/$personIdentifier")
        .willReturn(
          aResponse()
            .withStatus(status.value())
            .withHeader("Content-Type", "application/json")
            .withBody(jsonMapper.writeValueAsString(if (status == HttpStatus.OK) response else error)),
        ),
    )
  }

  fun stubDeleteExternalMovement(
    dpsId: UUID,
    status: HttpStatus = HttpStatus.NO_CONTENT,
  ) {
    dpsPersonLocationServer.stubFor(
      delete("/sync/external-movements/$dpsId")
        .willReturn(aResponse().withStatus(status.value())),
    )
  }

  fun stubDeleteCustodialSeries(
    dpsId: UUID,
    status: HttpStatus = HttpStatus.NO_CONTENT,
  ) {
    dpsPersonLocationServer.stubFor(
      delete("/sync/custodial-series/$dpsId")
        .willReturn(aResponse().withStatus(status.value())),
    )
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
}
