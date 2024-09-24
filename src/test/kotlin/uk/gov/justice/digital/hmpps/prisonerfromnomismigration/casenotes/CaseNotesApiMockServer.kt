package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.delete
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.http.HttpStatus.CREATED
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.test.context.junit.jupiter.SpringExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes.CaseNotesApiExtension.Companion.objectMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes.model.Author
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes.model.MigrationResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes.model.SyncCaseNoteRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes.model.SyncCaseNoteRequest.Source
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes.model.SyncResult
import java.time.LocalDateTime
import java.util.UUID

class CaseNotesApiExtension : BeforeAllCallback, AfterAllCallback, BeforeEachCallback {
  companion object {
    @JvmField
    val caseNotesApi = CaseNotesApiMockServer()
    lateinit var objectMapper: ObjectMapper
  }

  override fun beforeAll(context: ExtensionContext) {
    caseNotesApi.start()
    objectMapper = (SpringExtension.getApplicationContext(context).getBean("jacksonObjectMapper") as ObjectMapper)
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
      source = Source.NOMIS,
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

  fun stubMigrateCaseNotes(offenderNo: String, caseNotesIdPairs: List<Pair<Long, String>>) {
    stubFor(
      post("/migrate/case-notes")
        .withRequestBody(matchingJsonPath("$[0].personIdentifier", equalTo(offenderNo)))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(CREATED.value())
            .withBody(
              objectMapper.writeValueAsString(
                caseNotesIdPairs.map {
                  MigrationResult(UUID.fromString(it.second), legacyId = it.first)
                }.shuffled(),
              ),
            ),
        ),
    )
  }

  fun stubMigrateCaseNotesFailure(offenderNo: String) {
    stubFor(
      post("/migrate/case-notes")
        .withRequestBody(matchingJsonPath("$[0].personIdentifier", equalTo(offenderNo)))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(BAD_REQUEST.value())
            .withBody(
              objectMapper.writeValueAsString(
                ErrorResponse(
                  status = 400,
                  userMessage = "test message",
                  developerMessage = "dev message",
                ),
              ),
            ),
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
            objectMapper.writeValueAsString(
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
            objectMapper.writeValueAsString(
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
}
