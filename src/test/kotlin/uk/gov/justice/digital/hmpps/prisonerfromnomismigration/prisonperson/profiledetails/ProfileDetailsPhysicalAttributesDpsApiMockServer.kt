package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.profiledetails

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.PrisonPersonDpsApiExtension.Companion.dpsPrisonPersonApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.PrisonPersonDpsApiExtension.Companion.objectMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.model.ProfileDetailsPhysicalAttributesMigrationResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.model.ProfileDetailsPhysicalAttributesSyncResponse

@Component
class ProfileDetailsPhysicalAttributesDpsApiMockServer {

  fun stubSyncProfileDetailsPhysicalAttributes(
    response: ProfileDetailsPhysicalAttributesSyncResponse,
  ) {
    dpsPrisonPersonApi.stubFor(
      put(urlPathMatching("/sync/prisoners/.*/profile-details-physical-attributes"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(objectMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubSyncProfileDetailsPhysicalAttributes(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    dpsPrisonPersonApi.stubFor(
      put(urlPathMatching("/sync/prisoners/.*/profile-details-physical-attributes"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .withBody(objectMapper.writeValueAsString(error)),
        ),
    )
  }

  fun stubMigrateProfileDetailsPhysicalAttributes(
    offenderNo: String,
    response: ProfileDetailsPhysicalAttributesMigrationResponse,
  ) {
    dpsPrisonPersonApi.stubFor(
      put(urlPathMatching("/migration/prisoners/$offenderNo/profile-details-physical-attributes"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(objectMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubMigrateProfileDetailsPhysicalAttributes(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    dpsPrisonPersonApi.stubFor(
      put(urlPathMatching("/migration/prisoners/.*/profile-details-physical-attributes"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .withBody(objectMapper.writeValueAsString(error)),
        ),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = dpsPrisonPersonApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = dpsPrisonPersonApi.verify(count, pattern)
}
