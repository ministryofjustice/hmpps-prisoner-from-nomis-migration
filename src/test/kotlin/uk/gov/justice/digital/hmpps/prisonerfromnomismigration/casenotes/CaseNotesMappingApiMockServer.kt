package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes

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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CaseNoteMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CaseNoteMappingDto.MappingType.MIGRATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import java.util.*

@Component
class CaseNotesMappingApiMockServer(private val jsonMapper: JsonMapper) {
  fun stubGetByNomisId(
    caseNoteId: Long = 1,
    mapping: CaseNoteMappingDto = CaseNoteMappingDto(
      nomisBookingId = 123456,
      dpsCaseNoteId = UUID.randomUUID().toString(),
      nomisCaseNoteId = 1234567,
      offenderNo = "A1234KT",
      mappingType = MIGRATED,
    ),
  ) {
    mappingApi.stubFor(
      get(urlEqualTo("/mapping/casenotes/nomis-casenote-id/$caseNoteId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(jsonMapper.writeValueAsString(mapping)),
      ),
    )
  }

  fun stubGetByNomisId(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/casenotes/nomis-casenote-id/\\d+")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubGetByNomisId(
    caseNoteId: Long = 1,
    status: HttpStatus,
    error: ErrorResponse = ErrorResponse(status = status.value()),
  ) {
    mappingApi.stubFor(
      get(urlEqualTo("/mapping/casenotes/nomis-casenote-id/$caseNoteId")).willReturn(
        jsonResponse(error, status.value()),
      ),
    )
  }

  fun stubPostMapping() {
    mappingApi.stubFor(
      post("/mapping/casenotes").willReturn(
        jsonResponse(null, 201),
      ),
    )
  }

  fun stubPostMapping(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      post("/mapping/casenotes").willReturn(
        jsonResponse(error, status.value()),
      ),
    )
  }

  fun stubPostMappingFailureFollowedBySuccess() {
    mappingApi.stubMappingCreateFailureFollowedBySuccess(url = "/mapping/casenotes")
  }

  fun stubGetMappings(mappings: List<CaseNoteMappingDto>) {
    mappingApi.stubFor(
      post("/mapping/casenotes/nomis-casenote-id").willReturn(
        okJson(jsonMapper.writeValueAsString(mappings)),
      ),
    )
  }

  fun stubDeleteMapping() {
    mappingApi.stubFor(
      delete(urlPathMatching("/mapping/casenotes/dps-casenote-id/.*")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.NO_CONTENT.value()),
      ),
    )
  }

  fun stubGetByDpsId(dpsId: String, mappings: List<CaseNoteMappingDto>) {
    mappingApi.stubFor(
      get("/mapping/casenotes/dps-casenote-id/$dpsId/all").willReturn(
        okJson(jsonMapper.writeValueAsString(mappings)),
      ),
    )
  }

  fun stubPostMappingsBatch(status: HttpStatus = HttpStatus.CREATED) {
    mappingApi.stubFor(
      post("/mapping/casenotes/batch").willReturn(status(status.value())),
    )
  }

  fun stubPostMappingsBatch(error: DuplicateMappingErrorResponse) {
    mappingApi.stubFor(
      post("/mapping/casenotes/batch").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(409)
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubDeleteMapping(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      delete(urlPathMatching("/mapping/casenotes/dps-casenote-id/.*")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubUpdateMappingsByNomisId() {
    mappingApi.stubFor(
      put(urlPathMatching("/mapping/casenotes/merge/from/.+/to/.+")).willReturn(
        ok(),
      ),
    )
  }

  fun stubUpdateMappingsByNomisIdError(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      put(urlPathMatching("/mapping/casenotes/merge/from/.+/to/.+")).willReturn(
        jsonResponse(jsonMapper.writeValueAsString(error), status.value()),
      ),
    )
  }

  fun stubUpdateMappingsByBookingId(response: List<CaseNoteMappingDto>) {
    mappingApi.stubFor(
      put(urlPathMatching("/mapping/casenotes/merge/booking-id/.+/to/.+")).willReturn(
        okJson(jsonMapper.writeValueAsString(response)),
      ),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = mappingApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = mappingApi.verify(count, pattern)
  fun verify(count: CountMatchingStrategy, pattern: RequestPatternBuilder) = mappingApi.verify(count, pattern)
}
