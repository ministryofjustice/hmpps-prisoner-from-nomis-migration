package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.physicalattributes

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.PrisonPersonDpsApiExtension.Companion.dpsPrisonPersonApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.PrisonPersonDpsApiExtension.Companion.objectMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.model.PhysicalAttributesMigrationResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.model.PhysicalAttributesSyncResponse

@Component
class PhysAttrDpsApiMockServer {

  fun stubSyncPhysicalAttributes(
    response: PhysicalAttributesSyncResponse,
  ) {
    dpsPrisonPersonApi.stubFor(
      put(urlPathMatching("/sync/prisoners/.*/physical-attributes"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(objectMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubSyncPhysicalAttributes(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    dpsPrisonPersonApi.stubFor(
      put(urlPathMatching("/sync/prisoners/.*/physical-attributes"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .withBody(objectMapper.writeValueAsString(error)),
        ),
    )
  }

  fun stubMigratePhysicalAttributes(
    offenderNo: String,
    response: PhysicalAttributesMigrationResponse,
  ) {
    dpsPrisonPersonApi.stubFor(
      put(urlPathMatching("/migration/prisoners/$offenderNo/physical-attributes"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(objectMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubMigratePhysicalAttributes(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    dpsPrisonPersonApi.stubFor(
      put(urlPathMatching("/migration/prisoners/.*/physical-attributes"))
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
