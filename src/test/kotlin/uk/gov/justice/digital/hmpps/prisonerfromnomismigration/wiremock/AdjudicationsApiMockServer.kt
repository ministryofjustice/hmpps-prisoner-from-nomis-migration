package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.http.HttpStatus
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model.ChargeNumberMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model.HearingMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model.MigrateResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model.PunishmentMapping

class AdjudicationsApiExtension : BeforeAllCallback, AfterAllCallback, BeforeEachCallback {
  companion object {
    @JvmField
    val adjudicationsApi = AdjudicationsApiMockServer()
  }

  override fun beforeAll(context: ExtensionContext) {
    adjudicationsApi.start()
  }

  override fun beforeEach(context: ExtensionContext) {
    adjudicationsApi.resetAll()
  }

  override fun afterAll(context: ExtensionContext) {
    adjudicationsApi.stop()
  }
}

class AdjudicationsApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8087
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

  fun stubCreateAdjudicationForMigration(
    adjudicationNumber: Long = 654321,
    chargeSequence: Int = 1,
    chargeNumber: String = "654321-1",
    bookingId: Long = 87654,
    hearingIds: List<Pair<Long, Long>> = listOf(),
    punishmentIds: List<Pair<Int, Long>> = listOf(),
  ) {
    stubFor(
      post(WireMock.urlMatching("/reported-adjudications/migrate")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.CREATED.value())
          .withBody(
            MigrateResponse(
              chargeNumberMapping = ChargeNumberMapping(
                chargeNumber = chargeNumber,
                oicIncidentId = adjudicationNumber,
                offenceSequence = chargeSequence.toLong(),
              ),
              hearingMappings = hearingIds.map { (nomisHearingId, dpsHearingId) ->
                HearingMapping(
                  oicHearingId = nomisHearingId,
                  hearingId = dpsHearingId,
                )
              },
              punishmentMappings = punishmentIds.map { (sanctionSequence, dpsPunishmentId) ->
                PunishmentMapping(
                  bookingId = bookingId,
                  sanctionSeq = sanctionSequence.toLong(),
                  punishmentId = dpsPunishmentId,
                )
              },
            ).toJson(),
          ),
      ),
    )
  }

  fun verifyCreatedAdjudicationForMigration(builder: RequestPatternBuilder.() -> RequestPatternBuilder = { this }) =
    verify(
      postRequestedFor(WireMock.urlEqualTo("/reported-adjudications/migrate")).builder(),
    )

  fun createAdjudicationCount() =
    findAll(postRequestedFor(WireMock.urlMatching("/reported-adjudications/migrate"))).count()
}

fun Any.toJson(): String = ObjectMapper().writeValueAsString(this)
