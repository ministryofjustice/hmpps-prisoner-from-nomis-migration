package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.court

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatus.OK
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisSyncApiExtension.Companion.nomisSyncApi
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse
import java.util.*

@Component
class CourtSchedulerNomisSyncApiMockServer(private val jsonMapper: JsonMapper) {

  fun stubRecreateCourtScheduleInNomis(prisonerNumber: String, dpsCourtAppearanceId: UUID) {
    nomisSyncApi.stubFor(
      put(urlPathEqualTo("/court-scheduler/court/schedule/out/$prisonerNumber/$dpsCourtAppearanceId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(OK.value()),
      ),
    )
  }

  fun stubRecreateCourtScheduleInNomis(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    nomisSyncApi.stubFor(
      put(urlPathMatching("/court-scheduler/court/schedule/out/.*")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = nomisSyncApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = nomisSyncApi.verify(count, pattern)
}
