package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonMappingsResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi

@Component
class CorePersonMappingApiMockServer(private val jsonMapper: JsonMapper) {

  fun stubGetMappingsFromNomisIds(
    result: CorePersonMappingsResult = CorePersonMappingsResult(
      aliases = emptyList(),
      identifiers = emptyList(),
      addresses = emptyList(),
      contacts = emptyList(),
      addressUsages = emptyList(),
    ),
  ) {
    mappingApi.stubFor(
      post("/mapping/core-person/merge/mappings/by-nomis-ids").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(jsonMapper.writeValueAsString(result)),
      ),
    )
  }

  fun stubMerge() {
    mappingApi.stubFor(
      post("/mapping/core-person/merge").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value()),
      ),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = mappingApi.verify(pattern)
}
