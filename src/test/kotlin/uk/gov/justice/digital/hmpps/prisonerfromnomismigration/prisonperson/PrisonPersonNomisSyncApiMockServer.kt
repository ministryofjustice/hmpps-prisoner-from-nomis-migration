package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.havingExactly
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.PrisonPersonDpsApiExtension.Companion.objectMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisSyncApiExtension.Companion.nomisSyncApi

@Component
class PrisonPersonNomisSyncApiMockServer {
  fun stubSyncPhysicalAttributes(prisonerNumber: String = "A1234AA") {
    nomisSyncApi.stubFor(
      put(urlPathMatching("/prisonperson/$prisonerNumber/physical-attributes"))
        .withQueryParam("fields", havingExactly("HEIGHT", "WEIGHT", "BUILD", "FACE", "FACIAL_HAIR", "HAIR", "LEFT_EYE_COLOUR", "RIGHT_EYE_COLOUR", "SHOE_SIZE"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(200),
        ),
    )
  }

  fun stubSyncPhysicalAttributes(
    prisonerNumber: String = "A1234AA",
    status: HttpStatus,
  ) {
    nomisSyncApi.stubFor(
      put(urlPathMatching("/prisonperson/$prisonerNumber/physical-attributes"))
        .withQueryParam("fields", havingExactly("HEIGHT", "WEIGHT", "BUILD", "FACE", "FACIAL_HAIR", "HAIR", "LEFT_EYE_COLOUR", "RIGHT_EYE_COLOUR", "SHOE_SIZE"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .withBody(objectMapper.writeValueAsString(ErrorResponse(status = status.value()))),
        ),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = nomisSyncApi.verify(pattern)
}
