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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.model.DistinguishingMarkImageSyncResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.model.DistinguishingMarkSyncResponse
import java.util.UUID

@Component
class DistinguishingMarkImagesDpsApiMockServer {

  fun stubSyncCreateDistinguishingMarkImage(
    prisonerNumber: String,
    markId: UUID,
    response: DistinguishingMarkImageSyncResponse,
  ) {
    dpsPrisonPersonApi.stubFor(
      post(urlPathMatching("/sync/prisoners/$prisonerNumber/distinguishing-marks/$markId/images"))
        .willReturn(
          aResponse()
            .withStatus(201)
            .withHeader("Content-Type", "application/json")
            .withBody(objectMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubSyncCreateDistinguishingMarkImage(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    dpsPrisonPersonApi.stubFor(
      post(urlPathMatching("/sync/prisoners/.*/distinguishing-marks/.*/images"))
        .willReturn(
          aResponse()
            .withStatus(status.value())
            .withHeader("Content-Type", "application/json")
            .withBody(objectMapper.writeValueAsString(error)),
        ),
    )
  }

  fun stubSyncUpdateDistinguishingMarkImage(prisonerNumber: String, markId: UUID, imageId: UUID) {
    dpsPrisonPersonApi.stubFor(
      put(urlPathMatching("/sync/prisoners/$prisonerNumber/distinguishing-marks/$markId/images/$imageId"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(objectMapper.writeValueAsString(DistinguishingMarkImageSyncResponse(imageId))),
        ),
    )
  }

  fun stubSyncUpdateDistinguishingMarkImage(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    dpsPrisonPersonApi.stubFor(
      put(urlPathMatching("/sync/prisoners/.*/distinguishing-marks/.*/images/.*"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .withBody(objectMapper.writeValueAsString(error)),
        ),
    )
  }

  fun stubSyncDeleteDistinguishingMarkImage(prisonerNumber: String, markId: UUID, imageId: UUID) {
    dpsPrisonPersonApi.stubFor(
      delete(urlPathMatching("/sync/prisoners/$prisonerNumber/distinguishing-marks/$markId/images/$imageId"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(objectMapper.writeValueAsString(DistinguishingMarkSyncResponse(imageId))),
        ),
    )
  }

  fun stubSyncDeleteDistinguishingMarkImage(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    dpsPrisonPersonApi.stubFor(
      delete(urlPathMatching("/sync/prisoners/.*/distinguishing-marks/.*/images/.*"))
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
