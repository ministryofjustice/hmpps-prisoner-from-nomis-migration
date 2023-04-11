package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.delete
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.put
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.http.HttpStatus

class SentencingApiExtension : BeforeAllCallback, AfterAllCallback, BeforeEachCallback {
  companion object {
    @JvmField
    val sentencingApi = SentencingApiMockServer()
  }

  override fun beforeAll(context: ExtensionContext) {
    sentencingApi.start()
  }

  override fun beforeEach(context: ExtensionContext) {
    sentencingApi.resetRequests()
  }

  override fun afterAll(context: ExtensionContext) {
    sentencingApi.stop()
  }
}

class SentencingApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8085
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

  fun stubCreateSentencingAdjustmentForMigration(sentenceAdjustmentId: String = "654321") {
    stubFor(
      post(WireMock.urlMatching("/legacy/adjustments/migration")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.CREATED.value())
          .withBody("""{"adjustmentId": "$sentenceAdjustmentId"}"""),
      ),
    )
  }

  fun stubCreateSentencingAdjustmentForSynchronisation(sentenceAdjustmentId: String = "654321") {
    stubFor(
      post(WireMock.urlMatching("/legacy/adjustments")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.CREATED.value())
          .withBody("""{"adjustmentId": "$sentenceAdjustmentId"}"""),
      ),
    )
  }

  fun stubUpdateSentencingAdjustmentForSynchronisation(adjustmentId: String = "654321") {
    stubFor(
      put(WireMock.urlMatching("/legacy/adjustments/$adjustmentId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value()),
      ),
    )
  }

  fun stubDeleteSentencingAdjustmentForSynchronisation(adjustmentId: String = "654321") {
    stubFor(
      delete(WireMock.urlMatching("/legacy/adjustments/$adjustmentId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.NO_CONTENT.value()),
      ),
    )
  }

  fun stubDeleteSentencingAdjustmentForSynchronisationNotFound(adjustmentId: String = "654321") {
    stubFor(
      delete(WireMock.urlMatching("/legacy/adjustments/$adjustmentId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.NOT_FOUND.value()),
      ),
    )
  }

  fun createSentenceAdjustmentCount() =
    findAll(WireMock.postRequestedFor(WireMock.urlMatching("/legacy/adjustments/migration"))).count()

  fun createSentenceAdjustmentForSynchronisationCount() =
    findAll(WireMock.postRequestedFor(WireMock.urlMatching("/legacy/adjustments"))).count()
}
