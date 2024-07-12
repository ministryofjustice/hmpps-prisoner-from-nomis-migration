package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.delete
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.http.HttpStatus.CREATED
import org.springframework.test.context.junit.jupiter.SpringExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes.CaseNotesApiExtension.Companion.objectMapper
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
      caseNoteId = UUID.randomUUID().toString(),
      dummyAttribute = "text",
      // TBC
//      offenderIdentifier = "",
//      type = "",
//      typeDescription = "",
//      subType = "",
//      subTypeDescription = "",
//      source = "",
//      creationDateTime = LocalDateTime.now(),
//      occurrenceDateTime = LocalDateTime.now(),
//      authorName = "",
//      authorUserId = "",
//      text = "",
//      eventId = 0,
//      sensitive = false,
//      amendments = emptyList(),
      // locationId: String? = null
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

  fun stubMigrateCaseNotes(
    offenderNo: String,
    dpsCaseNotesIds: List<String>,
  ) {
    stubFor(
      post("/migrate/$offenderNo/casenotes").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(CREATED.value())
          .withBody(
            objectMapper.writeValueAsString(
              dpsCaseNotesIds.map {
                DpsCaseNote(
                  dummyAttribute = "qwerty",
                  caseNoteId = it,
                )
              },
            ),
          ),
      ),
    )
  }

  fun stubPostCaseNote(
    caseNoteRequest: SyncCaseNoteRequest,
  ) {
    stubFor(
      post("/sync/upsert").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(CREATED.value())
          .withBody(
            objectMapper.writeValueAsString(
              DpsCaseNote(
                dummyAttribute = caseNoteRequest.dummyAttribute,
                caseNoteId = caseNoteRequest.caseNoteId,
              ),
            ),
          ),
      ),
    )
  }

  fun stubDeleteCaseNote() {
    stubFor(
      delete(urlPathMatching("/sync/delete/.+"))
        .willReturn(
          aResponse()
            .withStatus(204)
            .withHeader("Content-Type", "application/json"),
        ),
    )
  }
//  fun createCaseNotesMigrationCount() =
//    findAll(postRequestedFor(urlMatching("/migrate/bookings/.*"))).count()
//
//  fun createCaseNotesSyncCount() =
//    findAll(postRequestedFor(urlMatching("/bookings/.*"))).count()
}
