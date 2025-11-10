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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.Location
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.NomisAudit
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.SyncAtAndBy
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.SyncResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.SyncWriteTapAuthorisation
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.SyncWriteTapOccurrence
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.TapLocation
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.TapMovementRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.TapMovementRequest.Direction.OUT
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

    @Suppress("unused")
    inline fun <reified T> getRequestBody(pattern: RequestPatternBuilder): T = dpsExtMovementsServer.getRequestBody(pattern, ExternalMovementsDpsApiExtension.Companion.objectMapper)
    inline fun <reified T> getRequestBodies(pattern: RequestPatternBuilder): List<T> = dpsExtMovementsServer.getRequestBodies(pattern, ExternalMovementsDpsApiExtension.Companion.objectMapper)

    fun syncTapAuthorisation() = SyncWriteTapAuthorisation(
      prisonCode = "LEI",
      statusCode = "APPROVED",
      absenceTypeCode = "RDR",
      absenceSubTypeCode = "RR",
      absenceReasonCode = "C5",
      accompaniedByCode = "U",
      repeat = false,
      fromDate = today.toLocalDate(),
      toDate = tomorrow.toLocalDate(),
      notes = "Authorisation comment",
      created = SyncAtAndBy(today, "AAA11A"),
      updated = SyncAtAndBy(today, "AAA11A"),
      legacyId = 1234,
    )

    fun syncTapOccurrence() = SyncWriteTapOccurrence(
      statusCode = "PENDING",
      releaseAt = today,
      returnBy = tomorrow,
      location = Location(
        description = "Boots",
        address = "High Street, Sheffield",
        postcode = "S1 1AA",
        uprn = "abcdef",
      ),
      absenceTypeCode = "RDR",
      absenceSubTypeCode = "RR",
      absenceReasonCode = "C5",
      accompaniedByCode = "P",
      transportCode = "VAN",
      notes = "Occurrence comment",
      created = SyncAtAndBy(today, "AAA11A"),
      updated = SyncAtAndBy(today, "AAA11A"),
      legacyId = 1234,
    )

    fun syncTemporaryAbsenceMovementRequest(occurrenceId: UUID? = UUID.randomUUID()) = TapMovementRequest(
      occurrenceId = occurrenceId,
      legacyId = "12345_154",
      movementDateTime = today,
      movementReason = "C5",
      direction = OUT,
      escort = "U",
      escortText = "Some escort text",
      prisonCode = "LEI",
      commentText = "Movement comment",
      location = TapLocation(
        description = "home",
        address = "123 Acacia Avenue, Birmingham",
        postcode = "B12 3AA",
      ),
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

  fun stubSyncTapAuthorisation(personIdentifier: String = "A1234BC", response: SyncResponse = syncResponse()) {
    dpsExtMovementsServer.stubFor(
      put("/sync/temporary-absence-authorisations/$personIdentifier")
        .willReturn(
          aResponse()
            .withStatus(201)
            .withHeader("Content-Type", "application/json")
            .withBody(objectMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubSyncTapAuthorisationError(
    personIdentifier: String = "A1234BC",
    status: Int = 500,
    error: ErrorResponse = ErrorResponse(status = status),
  ) {
    dpsExtMovementsServer.stubFor(
      put("/sync/temporary-absence-authorisations/$personIdentifier")
        .willReturn(
          aResponse()
            .withStatus(status)
            .withHeader("Content-Type", "application/json")
            .withBody(objectMapper.writeValueAsString(error)),
        ),
    )
  }

  fun stubSyncTapOccurrence(authorisationId: UUID, response: SyncResponse = syncResponse()) {
    dpsExtMovementsServer.stubFor(
      put("/sync/temporary-absence-authorisations/$authorisationId/occurrences")
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(objectMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubSyncTapOccurrenceError(
    authorisationId: UUID,
    status: Int = 500,
    error: ErrorResponse = ErrorResponse(status = status),
  ) {
    dpsExtMovementsServer.stubFor(
      put("/sync/temporary-absence-authorisations/$authorisationId/occurrences")
        .willReturn(
          aResponse()
            .withStatus(status)
            .withHeader("Content-Type", "application/json")
            .withBody(objectMapper.writeValueAsString(error)),
        ),
    )
  }

  fun stubSyncTemporaryAbsenceMovement(personIdentifier: String = "A1234BC", response: SyncResponse = syncResponse()) {
    dpsExtMovementsServer.stubFor(
      put("/sync/temporary-absence-movement/$personIdentifier")
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(objectMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubSyncTemporaryAbsenceMovementError(
    personIdentifier: String = "A1234BC",
    status: Int = 500,
    error: ErrorResponse = ErrorResponse(status = status),
  ) {
    dpsExtMovementsServer.stubFor(
      put("/sync/temporary-absence-movement/$personIdentifier")
        .willReturn(
          aResponse()
            .withStatus(status)
            .withHeader("Content-Type", "application/json")
            .withBody(objectMapper.writeValueAsString(error)),
        ),
    )
  }
}
