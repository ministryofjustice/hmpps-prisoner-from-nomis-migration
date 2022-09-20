package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.patch
import com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlMatching
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.http.HttpStatus

class IncentivesApiExtension : BeforeAllCallback, AfterAllCallback, BeforeEachCallback {
  companion object {
    @JvmField
    val incentivesApi = IncentivesApiMockServer()
  }

  override fun beforeAll(context: ExtensionContext) {
    incentivesApi.start()
  }

  override fun beforeEach(context: ExtensionContext) {
    incentivesApi.resetRequests()
  }

  override fun afterAll(context: ExtensionContext) {
    incentivesApi.stop()
  }
}

class IncentivesApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8084
  }

  fun stubHealthPing(status: Int) {
    stubFor(
      get("/health/ping").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(if (status == 200) "pong" else "some error")
          .withStatus(status)
      )
    )
  }

  fun stubCreateIncentive(incentiveId: Long = 654321) {
    stubFor(
      post(urlMatching("/iep/migration/booking/\\d*")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.CREATED.value())
          .withBody("""{"id": $incentiveId}""")
      )
    )
  }

  fun stubCreateSynchroniseIncentive() {
    stubFor(
      post(urlMatching("/iep/sync/booking/\\d*")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.CREATED.value())
          .withBody("""{"id": 654321}""")
      )
    )
  }

  fun stubUpdateSynchroniseIncentive() {
    stubFor(
      patch(urlMatching("/iep/sync/booking/\\d*/id/\\d*")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
      )
    )
  }

  fun verifyUpdateSynchroniseIncentive(times: Int) {
    verify(
      times,
      patchRequestedFor(
        urlMatching("/iep/sync/booking/\\d*/id/\\d*")
      )
    )
  }

  fun verifyCreateSynchroniseIncentive() {
    verify(
      postRequestedFor(
        urlMatching("/iep/sync/booking/\\d*")
      )
    )
  }

  fun createIncentiveCount() = findAll(postRequestedFor(urlMatching("/iep/migration/booking/\\d*"))).count()

  fun createIncentiveSynchronisationCount() = findAll(postRequestedFor(urlMatching("/iep/sync/booking/\\d*"))).count()
}
