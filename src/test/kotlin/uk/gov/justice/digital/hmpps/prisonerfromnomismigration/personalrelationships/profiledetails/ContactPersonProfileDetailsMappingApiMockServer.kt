package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.profiledetails

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.CountMatchingStrategy
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi

@Component
class ContactPersonProfileDetailsMappingApiMockServer(private val objectMapper: ObjectMapper) {
  fun stubPutMapping() {
    mappingApi.stubFor(
      put("/mapping/contact-person/profile-details/migration").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(200),
      ),
    )
  }

  fun stubPutMapping(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      put("/mapping/contact-person/profile-details/migration").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubPutMappingFailureFollowedBySuccess() {
    mappingApi.stubMappingUpdateFailureFollowedBySuccess(url = "/mapping/contact-person/profile-details/migration")
  }

  fun verify(pattern: RequestPatternBuilder) = mappingApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = mappingApi.verify(count, pattern)
  fun verify(count: CountMatchingStrategy, pattern: RequestPatternBuilder) = mappingApi.verify(count, pattern)
}
