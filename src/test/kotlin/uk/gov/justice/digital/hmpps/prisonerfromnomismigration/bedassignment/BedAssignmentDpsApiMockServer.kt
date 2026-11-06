package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.bedassignment

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.jsonResponse
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.client.WireMock.status
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.test.context.junit.jupiter.SpringExtension
import tools.jackson.databind.json.JsonMapper

class BedAssignmentDpsApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val bedAssignmentApi = BedAssignmentDpsApiMockServer()
    lateinit var jsonMapper: JsonMapper
  }

  override fun beforeAll(context: ExtensionContext) {
    bedAssignmentApi.start()
    jsonMapper = SpringExtension.getApplicationContext(context).getBean("jacksonJsonMapper") as JsonMapper
  }

  override fun beforeEach(context: ExtensionContext) {
    bedAssignmentApi.resetAll()
  }

  override fun afterAll(context: ExtensionContext) {
    bedAssignmentApi.stop()
  }
}

class BedAssignmentDpsApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    const val WIREMOCK_PORT = 8112
  }

  fun stubMigrate(offenderNo: String, response: String = "[]") {
    stubFor(post("/nomis-sync/bed-assignments/migrate/$offenderNo").willReturn(jsonResponse(response, 201)))
  }

  fun stubMigrateError(offenderNo: String, status: Int = 500) {
    stubFor(post("/nomis-sync/bed-assignments/migrate/$offenderNo").willReturn(status(status)))
  }

  fun stubSync(offenderNo: String, response: String = """{"created":[],"updated":[],"deleted":[]}""") {
    stubFor(put("/nomis-sync/bed-assignments/sync/$offenderNo").willReturn(jsonResponse(response, 200)))
  }

  fun stubSyncError(offenderNo: String, status: Int = 500) {
    stubFor(put("/nomis-sync/bed-assignments/sync/$offenderNo").willReturn(status(status)))
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
