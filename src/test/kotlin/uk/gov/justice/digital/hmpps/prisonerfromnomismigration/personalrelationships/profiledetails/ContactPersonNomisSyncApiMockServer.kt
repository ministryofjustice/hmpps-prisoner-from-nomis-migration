package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.profiledetails

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.ContactPersonDpsApiExtension.Companion.jsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisSyncApiExtension.Companion.nomisSyncApi

@Component
class ContactPersonNomisSyncApiMockServer {
  fun stubSyncProfileDetails(prisonerNumber: String = "A1234AA", profileType: String = "MARITAL") {
    nomisSyncApi.stubFor(
      put(urlPathMatching("/contactperson/sync/profile-details/$prisonerNumber/$profileType"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(200),
        ),
    )
  }

  fun stubSyncProfileDetails(
    prisonerNumber: String = "A1234AA",
    profileType: String = "MARITAL",
    status: HttpStatus,
  ) {
    nomisSyncApi.stubFor(
      put(urlPathMatching("/contactperson/sync/profile-details/$prisonerNumber/$profileType"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .withBody(jsonMapper.writeValueAsString(ErrorResponse(status = status.value()))),
        ),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = nomisSyncApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = nomisSyncApi.verify(count, pattern)
}
