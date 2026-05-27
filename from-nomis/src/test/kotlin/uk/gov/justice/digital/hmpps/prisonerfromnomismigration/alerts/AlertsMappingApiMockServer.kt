package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts

import com.github.tomakehurst.wiremock.client.CountMatchingStrategy
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.delete
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.AlertMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.AlertMappingDto.MappingType.MIGRATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import java.util.UUID

@Component
class AlertsMappingApiMockServer(private val jsonMapper: JsonMapper) {
  fun stubGetByNomisId(
    bookingId: Long = 123456,
    alertSequence: Long = 1,
    mapping: AlertMappingDto = AlertMappingDto(
      nomisBookingId = 123456,
      nomisAlertSequence = 1,
      dpsAlertId = UUID.randomUUID().toString(),
      offenderNo = "A1234KT",
      mappingType = MIGRATED,
    ),
  ) {
    mappingApi.stubFor(
      get(urlEqualTo("/mapping/alerts/nomis-booking-id/$bookingId/nomis-alert-sequence/$alertSequence")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(jsonMapper.writeValueAsString(mapping)),
      ),
    )
  }

  fun stubGetByNomisId(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/alerts/nomis-booking-id/\\d+/nomis-alert-sequence/\\d+")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubGetByNomisId(
    bookingId: Long = 123456,
    alertSequence: Long = 1,
    status: HttpStatus,
    error: ErrorResponse = ErrorResponse(status = status.value()),
  ) {
    mappingApi.stubFor(
      get(urlEqualTo("/mapping/alerts/nomis-booking-id/$bookingId/nomis-alert-sequence/$alertSequence")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubPostMapping() {
    mappingApi.stubFor(
      post("/mapping/alerts").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(201),
      ),
    )
  }

  fun stubPostMapping(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      post("/mapping/alerts").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubPostMappingFailureFollowedBySuccess() {
    mappingApi.stubMappingCreateFailureFollowedBySuccess(url = "/mapping/alerts")
  }

  fun stubPostBatchMappings() {
    mappingApi.stubFor(
      post("/mapping/alerts/batch").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(201),
      ),
    )
  }

  fun stubPostBatchMappings(error: DuplicateMappingErrorResponse) {
    mappingApi.stubFor(
      post("/mapping/alerts/batch").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(409)
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubReplaceMappings(offenderNo: String) {
    mappingApi.stubFor(
      put("/mapping/alerts/$offenderNo/all").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(200),
      ),
    )
  }
  fun stubReplaceMappingsFailureFollowedBySuccess(offenderNo: String) {
    mappingApi.stubMappingUpdateFailureFollowedBySuccess(url = "/mapping/alerts/$offenderNo/all")
  }

  fun stubReplaceMappingsForMerge(offenderNo: String) {
    mappingApi.stubFor(
      put("/mapping/alerts/$offenderNo/merge").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(200),
      ),
    )
  }

  fun stubReplaceMappingsForMergeFollowedBySuccess(offenderNo: String) {
    mappingApi.stubMappingUpdateFailureFollowedBySuccess(url = "/mapping/alerts/$offenderNo/merge")
  }

  fun stubDeleteMapping() {
    mappingApi.stubFor(
      delete(urlPathMatching("/mapping/alerts/dps-alert-id/.*")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.NO_CONTENT.value()),
      ),
    )
  }

  fun stubDeleteMapping(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      delete(urlPathMatching("/mapping/alerts/dps-alert-id/.*")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = mappingApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = mappingApi.verify(count, pattern)
  fun verify(count: CountMatchingStrategy, pattern: RequestPatternBuilder) = mappingApi.verify(count, pattern)
}
