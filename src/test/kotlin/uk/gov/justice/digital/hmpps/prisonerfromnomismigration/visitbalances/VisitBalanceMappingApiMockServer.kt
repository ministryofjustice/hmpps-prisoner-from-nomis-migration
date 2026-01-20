package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visitbalances

import com.github.tomakehurst.wiremock.client.CountMatchingStrategy
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.VisitBalanceAdjustmentMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi

@Component
class VisitBalanceMappingApiMockServer(private val jsonMapper: JsonMapper) {
  fun stubCreateVisitBalanceAdjustmentMapping() {
    mappingApi.stubFor(
      post("/mapping/visit-balance-adjustment").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(201),
      ),
    )
  }

  fun stubCreateVisitBalanceAdjustmentMapping(error: DuplicateMappingErrorResponse) {
    mappingApi.stubFor(
      post("/mapping/visit-balance-adjustment").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(409)
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateVisitBalanceAdjustmentMappingFailureFollowedBySuccess() = mappingApi.stubMappingCreateFailureFollowedBySuccess(url = "/mapping/visit-balance-adjustment")

  fun stubGetVisitBalanceAdjustmentByNomisIdOrNull(
    nomisVisitBalanceAdjustmentId: Long = 12345,
    mapping: VisitBalanceAdjustmentMappingDto? = VisitBalanceAdjustmentMappingDto(
      nomisVisitBalanceAdjustmentId = nomisVisitBalanceAdjustmentId,
      dpsId = "A1234BC",
      mappingType = VisitBalanceAdjustmentMappingDto.MappingType.NOMIS_CREATED,
    ),
  ) {
    mapping?.apply {
      mappingApi.stubFor(
        get(urlEqualTo("/mapping/visit-balance-adjustment/nomis-id/$nomisVisitBalanceAdjustmentId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(jsonMapper.writeValueAsString(mapping)),
        ),
      )
    } ?: run {
      mappingApi.stubFor(
        get(urlEqualTo("/mapping/visit-balance-adjustment/nomis-id/$nomisVisitBalanceAdjustmentId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody(jsonMapper.writeValueAsString(ErrorResponse(status = 404))),
        ),
      )
    }
  }

  fun stubGetVisitBalanceAdjustmentByNomisId(
    nomisVisitBalanceAdjustmentId: Long = 12345,
    mapping: VisitBalanceAdjustmentMappingDto? = VisitBalanceAdjustmentMappingDto(
      nomisVisitBalanceAdjustmentId = nomisVisitBalanceAdjustmentId,
      dpsId = "A1234BC",
      mappingType = VisitBalanceAdjustmentMappingDto.MappingType.NOMIS_CREATED,
    ),
  ) = stubGetVisitBalanceAdjustmentByNomisIdOrNull(nomisVisitBalanceAdjustmentId, mapping)

  fun verify(pattern: RequestPatternBuilder) = mappingApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = mappingApi.verify(count, pattern)
  fun verify(count: CountMatchingStrategy, pattern: RequestPatternBuilder) = mappingApi.verify(count, pattern)
  fun resetAll() = mappingApi.resetAll()
}
