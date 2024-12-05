package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.identifyingmarks

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.delete
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.PrisonPersonDpsApiExtension.Companion.dpsPrisonPersonApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.PrisonPersonDpsApiExtension.Companion.objectMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.model.DistinguishingMarkSyncResponse
import java.util.UUID

@Component
class DistinguishingMarksDpsApiMockServer {

  fun stubSyncCreateDistinguishingMark(
    prisonerNumber: String,
    response: DistinguishingMarkSyncResponse,
  ) {
    dpsPrisonPersonApi.stubFor(
      post(urlPathMatching("/sync/prisoners/$prisonerNumber/distinguishing-marks"))
        .willReturn(
          aResponse()
            .withStatus(201)
            .withHeader("Content-Type", "application/json")
            .withBody(objectMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubSyncCreateDistinguishingMark(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    dpsPrisonPersonApi.stubFor(
      post(urlPathMatching("/sync/prisoners/.*/distinguishing-marks"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .withBody(objectMapper.writeValueAsString(error)),
        ),
    )
  }

  fun stubSyncUpdateDistinguishingMark(prisonerNumber: String, dpsId: UUID) {
    dpsPrisonPersonApi.stubFor(
      put(urlPathMatching("/sync/prisoners/$prisonerNumber/distinguishing-marks/$dpsId"))
        .willReturn(
          aResponse()
            .withStatus(201)
            .withHeader("Content-Type", "application/json")
            .withBody(objectMapper.writeValueAsString(DistinguishingMarkSyncResponse(dpsId))),
        ),
    )
  }

  fun stubSyncUpdateDistinguishingMark(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    dpsPrisonPersonApi.stubFor(
      put(urlPathMatching("/sync/prisoners/.*/distinguishing-marks/.*"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .withBody(objectMapper.writeValueAsString(error)),
        ),
    )
  }

  fun stubSyncDeleteDistinguishingMark(prisonerNumber: String, dpsId: UUID) {
    dpsPrisonPersonApi.stubFor(
      delete(urlPathMatching("/sync/prisoners/$prisonerNumber/distinguishing-marks/$dpsId"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(objectMapper.writeValueAsString(DistinguishingMarkSyncResponse(dpsId))),
        ),
    )
  }

  fun stubSyncDeleteDistinguishingMark(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    dpsPrisonPersonApi.stubFor(
      delete(urlPathMatching("/sync/prisoners/.*/distinguishing-marks/.*"))
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
