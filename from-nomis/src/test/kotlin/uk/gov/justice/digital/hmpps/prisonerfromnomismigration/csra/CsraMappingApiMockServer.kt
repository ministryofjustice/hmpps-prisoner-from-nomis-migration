package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csra

import com.github.tomakehurst.wiremock.client.CountMatchingStrategy
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.delete
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.jsonResponse
import com.github.tomakehurst.wiremock.client.WireMock.ok
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.client.WireMock.status
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CsraMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CsraMappingDto.MappingType.MIGRATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import java.util.UUID

@Component
class CsraMappingApiMockServer(private val jsonMapper: JsonMapper) {
  fun stubGetByNomisId(
    bookingId: Long = 123456,
    sequence: Int = 5,
    mapping: CsraMappingDto = CsraMappingDto(
      nomisBookingId = bookingId,
      nomisSequence = sequence,
      dpsCsraId = UUID.randomUUID().toString(),
      offenderNo = "A1234KT",
      mappingType = MIGRATED,
    ),
  ) {
    mappingApi.stubFor(
      get(urlEqualTo("/mapping/csras/booking-id/$bookingId/sequence/$sequence")).willReturn(
        okJson(jsonMapper.writeValueAsString(mapping)),
      ),
    )
  }

  fun stubGetByNomisId(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/csras/booking-id/\\d+/sequence/\\d+"))
        .willReturn(jsonResponse(error, status.value())),
    )
  }

  fun stubGetByNomisId(
    bookingId: Long = 123456,
    sequence: Int = 5,
    status: HttpStatus,
    error: ErrorResponse = ErrorResponse(status = status.value()),
  ) {
    mappingApi.stubFor(
      get(urlEqualTo("/mapping/csras/booking-id/$bookingId/sequence/$sequence")).willReturn(
        jsonResponse(error, status.value()),
      ),
    )
  }

  fun stubPostMapping(offenderNo: String) {
    mappingApi.stubFor(post("/mapping/csras/$offenderNo/all").willReturn(status(201)))
  }

  fun stubPostMapping(
    offenderNo: String,
    status: HttpStatus,
    error: ErrorResponse = ErrorResponse(status = status.value()),
  ) {
    mappingApi.stubFor(
      post("/mapping/csras/$offenderNo/all").willReturn(jsonResponse(error, status.value())),
    )
  }

  fun stubPostMapping(offenderNo: String, error: DuplicateMappingErrorResponse) {
    mappingApi.stubFor(
      post("/mapping/csras/$offenderNo/all").willReturn(jsonResponse(error, 409)),
    )
  }

  fun stubPostMappingFailureFollowedBySuccess(offenderNo: String) {
    mappingApi.stubMappingCreateFailureFollowedBySuccess(url = "/mapping/csras/$offenderNo/all")
  }

  fun stubDeleteMapping() {
    mappingApi.stubFor(
      delete(urlPathMatching("/mapping/csras/dps-csra-id/.*")).willReturn(status(204)),
    )
  }

  fun stubGetByDpsId(dpsId: String, mappings: List<CsraMappingDto>) {
    mappingApi.stubFor(
      get("/mapping/csras/dps-csra-id/$dpsId").willReturn(
        okJson(jsonMapper.writeValueAsString(mappings)),
      ),
    )
  }

  fun stubPostMappingsBatch(status: HttpStatus = HttpStatus.CREATED) {
    mappingApi.stubFor(
      post("/mapping/csras/batch").willReturn(status(status.value())),
    )
  }

  fun stubPostMappingsBatch(error: DuplicateMappingErrorResponse) {
    mappingApi.stubFor(
      post("/mapping/csras/batch").willReturn(jsonResponse(error, 409)),
    )
  }

  fun stubDeleteMapping(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      delete(urlPathMatching("/mapping/csras/dps-csra-id/.*")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubUpdateMappingsByNomisId() {
    mappingApi.stubFor(
      put(urlPathMatching("/mapping/csras/merge/from/.+/to/.+")).willReturn(
        ok(),
      ),
    )
  }

  fun stubUpdateMappingsByNomisIdError(
    status: HttpStatus,
    error: ErrorResponse = ErrorResponse(status = status.value()),
  ) {
    mappingApi.stubFor(
      put(urlPathMatching("/mapping/csras/merge/from/.+/to/.+")).willReturn(
        jsonResponse(jsonMapper.writeValueAsString(error), status.value()),
      ),
    )
  }

  fun stubUpdateMappingsByBookingId(response: List<CsraMappingDto>) {
    mappingApi.stubFor(
      put(urlPathMatching("/mapping/csras/merge/booking-id/.+/to/.+")).willReturn(
        okJson(jsonMapper.writeValueAsString(response)),
      ),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = mappingApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = mappingApi.verify(count, pattern)
  fun verify(count: CountMatchingStrategy, pattern: RequestPatternBuilder) = mappingApi.verify(count, pattern)
}
