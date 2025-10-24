package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits

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
import org.springframework.test.context.junit.jupiter.SpringExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.OfficialVisitsDpsApiExtension.Companion.objectMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.IdPair
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.MigrateVisitConfigRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.MigrateVisitConfigResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.MigrateVisitSlot
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.getRequestBody
import java.time.LocalDate
import java.util.UUID

class OfficialVisitsDpsApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val dpsOfficialVisitsServer = OfficialVisitsDpsApiMockServer()
    lateinit var objectMapper: ObjectMapper

    inline fun <reified T> getRequestBody(pattern: RequestPatternBuilder): T = dpsOfficialVisitsServer.getRequestBody(pattern, objectMapper)
  }

  override fun beforeAll(context: ExtensionContext) {
    dpsOfficialVisitsServer.start()
    objectMapper = (SpringExtension.getApplicationContext(context).getBean("jacksonObjectMapper") as ObjectMapper)
  }

  override fun beforeEach(context: ExtensionContext) {
    dpsOfficialVisitsServer.resetAll()
  }

  override fun afterAll(context: ExtensionContext) {
    dpsOfficialVisitsServer.stop()
  }
}

class OfficialVisitsDpsApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8104
    fun migrateVisitSlot() = MigrateVisitSlot(
      agencyVisitSlotId = 123,
      dpsLocationId = UUID.randomUUID(),
      maxGroups = 99,
      maxAdults = 99,
    )
    fun migrateVisitConfigRequest() = MigrateVisitConfigRequest(
      prisonCode = "MDI",
      dayCode = "MON",
      timeSlotSeq = 1,
      startTime = "10:00",
      endTime = "11:00",
      effectiveDate = LocalDate.parse("2021-01-01"),
      visitSlots = listOf(migrateVisitSlot()),
    )

    fun migrateVisitConfigResponse(request: MigrateVisitConfigRequest = migrateVisitConfigRequest()) = MigrateVisitConfigResponse(
      prisonCode = request.prisonCode,
      dayCode = request.dayCode,
      timeSlotSeq = request.timeSlotSeq,
      dpsTimeSlotId = 678,
      visitSlots = request.visitSlots.map {
        IdPair(
          elementType = IdPair.ElementType.PRISON_VISIT_SLOT,
          nomisId = it.agencyVisitSlotId,
          dpsId = it.agencyVisitSlotId * 10,
        )
      },
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

  fun stubMigrateVisitConfiguration(response: MigrateVisitConfigResponse = migrateVisitConfigResponse()) {
    stubFor(
      post("/migrate/visit-configuration")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(201)
            .withBody(objectMapper.writeValueAsString(response)),
        ),
    )
  }
}
