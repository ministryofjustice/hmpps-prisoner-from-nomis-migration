package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.http.HttpStatus.CREATED
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.test.context.junit.jupiter.SpringExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.FinanceApiExtension.Companion.objectMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.model.InitialPrisonerBalance
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.model.InitialPrisonerBalancesRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.model.SyncTransactionReceipt
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.getRequestBodies
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.getRequestBody
import java.math.BigDecimal

class FinanceApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val financeApi = FinanceApiMockServer()
    lateinit var objectMapper: ObjectMapper

    @Suppress("unused")
    inline fun <reified T> getRequestBody(pattern: RequestPatternBuilder): T = financeApi.getRequestBody(
      pattern,
      objectMapper,
    )
    inline fun <reified T> getRequestBodies(pattern: RequestPatternBuilder): List<T> = financeApi.getRequestBodies(
      pattern,
      objectMapper,
    )
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

    fun prisonerBalanceMigrationDto() = InitialPrisonerBalancesRequest(
      prisonId = "ASI",
      initialBalances = listOf(
        InitialPrisonerBalance(
          // TODO Check if need these
          // prisonId = "ASI",
          // lastTransactionId = 173,
          accountCode = 2101,
          balance = BigDecimal.valueOf(23.50),
          holdBalance = BigDecimal.valueOf(1.25),
        ),
        InitialPrisonerBalance(
          // prisonId = "ASI",
          // lastTransactionId = 174,
          accountCode = 2102,
          balance = BigDecimal.valueOf(11.50),
          holdBalance = BigDecimal.ZERO,
        ),
      ),
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

  fun stubMigratePrisonerBalance(prisonNumber: String = "A1234BC") {
    stubFor(
      post("/migrate/prisoner-balances/$prisonNumber")
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json"),
        ),
    )
  }
}
