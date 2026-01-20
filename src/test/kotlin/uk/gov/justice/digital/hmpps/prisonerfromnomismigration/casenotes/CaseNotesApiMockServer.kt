package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.delete
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.client.WireMock.status
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.http.HttpStatus.CREATED
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.test.context.junit.jupiter.SpringExtension
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes.CaseNotesApiExtension.Companion.jsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes.model.Author
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes.model.SyncCaseNoteRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes.model.SyncResult
import java.time.LocalDateTime
import java.util.UUID

class CaseNotesApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val caseNotesApi = CaseNotesApiMockServer()
    lateinit var jsonMapper: JsonMapper
  }

  override fun beforeAll(context: ExtensionContext) {
    caseNotesApi.start()
    jsonMapper = (SpringExtension.getApplicationContext(context).getBean("jacksonJsonMapper") as JsonMapper)
  }

  override fun beforeEach(context: ExtensionContext) {
    caseNotesApi.resetAll()
  }

  override fun afterAll(context: ExtensionContext) {
    caseNotesApi.stop()
  }
}

class CaseNotesApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8096

    fun dpsCaseNote() = SyncCaseNoteRequest(
      id = UUID.randomUUID(),
      legacyId = 12345L,
      personIdentifier = "A1234AA",
      locationId = "SWI",
      type = "X",
      subType = "Y",
      text = "the actual casenote",
      systemGenerated = false,
      createdDateTime = LocalDateTime.parse("2021-02-03T04:05:06"),
      createdByUsername = "the-computer",
      author = Author(
        username = "me",
        userId = "123456",
        firstName = "First",
        lastName = "Last",
      ),
      amendments = emptySet(),
      occurrenceDateTime = LocalDateTime.parse("2021-02-03T04:05:06"),
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

  fun stubPutCaseNote(caseNoteRequest: SyncCaseNoteRequest) {
    stubFor(
      put("/sync/case-notes").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(CREATED.value())
          .withBody(
            jsonMapper.writeValueAsString(
              SyncResult(
                id = caseNoteRequest.id ?: UUID.randomUUID(),
                legacyId = caseNoteRequest.legacyId,
                action = if (caseNoteRequest.id == null) SyncResult.Action.CREATED else SyncResult.Action.UPDATED,
              ),
            ),
          ),
      ),
    )
  }

  fun stubPutCaseNoteFailure() {
    stubFor(
      put("/sync/case-notes").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(INTERNAL_SERVER_ERROR.value())
          .withBody(
            jsonMapper.writeValueAsString(
              ErrorResponse(
                status = 500,
                userMessage = "test message",
                developerMessage = "dev message",
              ),
            ),
          ),
      ),
    )
  }

  fun stubDeleteCaseNote() {
    stubFor(
      delete(urlPathMatching("/sync/case-notes/.+"))
        .willReturn(
          aResponse()
            .withStatus(204)
            .withHeader("Content-Type", "application/json"),
        ),
    )
  }

  fun stubDeleteCaseNoteFailure() {
    stubFor(
      delete(urlPathMatching("/sync/case-notes/.+"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(INTERNAL_SERVER_ERROR.value()),
        ),
    )
  }

  fun stubMoveCaseNotes(status: Int = 200) {
    stubFor(
      put("/move/case-notes")
        .willReturn(status(status)),
    )
  }
}
