package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.http.HttpStatus.CREATED
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.test.context.junit.jupiter.SpringExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes.model.Author
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes.model.SyncCaseNoteRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.FinanceApiExtension.Companion.objectMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.model.SyncTransactionReceipt
import java.time.LocalDateTime
import java.util.UUID

class FinanceApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val financeApi = FinanceApiMockServer()
    lateinit var objectMapper: ObjectMapper
  }

  override fun beforeAll(context: ExtensionContext) {
    financeApi.start()
    objectMapper = (SpringExtension.getApplicationContext(context).getBean("jacksonObjectMapper") as ObjectMapper)
  }

  override fun beforeEach(context: ExtensionContext) {
    financeApi.resetAll()
  }

  override fun afterAll(context: ExtensionContext) {
    financeApi.stop()
  }
}

class FinanceApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8102

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

  fun stubPostOffenderTransaction(response: SyncTransactionReceipt) {
    stubFor(
      post("/sync/offender-transactions").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(CREATED.value())
          .withBody(objectMapper.writeValueAsString(response)),
      ),
    )
  }

  fun stubPostOffenderTransactionFailure() {
    stubFor(
      post("/sync/offender-transactions").willReturn(
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

  fun stubPostGLTransaction(response: SyncTransactionReceipt) {
    stubFor(
      post("/sync/general-ledger-transactions").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(CREATED.value())
          .withBody(objectMapper.writeValueAsString(response)),
      ),
    )
  }

  fun stubPostGLTransactionFailure() {
    stubFor(
      post("/sync/general-ledger-transactions").willReturn(
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
}
