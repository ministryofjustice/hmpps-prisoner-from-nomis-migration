package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes

import com.fasterxml.jackson.databind.ObjectMapper
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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CaseNoteMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CaseNoteMappingDto.MappingType.MIGRATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.pageContent
import java.time.LocalDateTime
import java.util.*

@Component
class CaseNotesMappingApiMockServer(private val objectMapper: ObjectMapper) {
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
          .withBody(objectMapper.writeValueAsString(mapping)),
      ),
    )
  }

  fun stubGetByNomisId(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/casenotes/nomis-casenote-id/\\d+")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(objectMapper.writeValueAsString(error)),
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

  fun stubPostBatchMappings(offenderNo: String) {
    mappingApi.stubFor(
      post("/mapping/casenotes/$offenderNo/all").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(201),
      ),
    )
  }

  fun stubPostBatchMappingsFailureFollowedBySuccess(offenderNo: String) {
    mappingApi.stubMappingCreateFailureFollowedBySuccess(url = "/mapping/casenotes/$offenderNo/all")
  }

  fun stubPostBatchMappings(offenderNo: String, error: DuplicateMappingErrorResponse) {
    mappingApi.stubFor(
      post("/mapping/casenotes/$offenderNo/all").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(409)
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubMigrationCount(recordsMigrated: Long) {
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/casenotes/migration-id/.*/grouped-by-booking")).willReturn(
        okJson(
          """
            {
              "totalElements": $recordsMigrated,
              "content": [
                {
                  "whenCreated": "${LocalDateTime.now()}"
                }
              ]           
            }
          """.trimIndent(),
        ),
      ),
    )
  }

  fun stubGetMappings(mappings: List<CaseNoteMappingDto>) {
    mappingApi.stubFor(
      post("/mapping/casenotes/nomis-casenote-id").willReturn(
        okJson(objectMapper.writeValueAsString(mappings)),
      ),
    )
  }

  fun stubGetMappingsError(status: HttpStatus) {
    mappingApi.stubFor(
      post("/mapping/casenotes/nomis-casenote-id").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value()),
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
        okJson(objectMapper.writeValueAsString(mappings)),
      ),
    )
  }

  fun stubPostMappingsBatch(status: HttpStatus = HttpStatus.CREATED) {
    mappingApi.stubFor(
      post("/mapping/casenotes/batch").willReturn(status(status.value())),
    )
  }

  fun stubDeleteMapping(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      delete(urlPathMatching("/mapping/casenotes/dps-casenote-id/.*")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubSingleItemByMigrationId(migrationId: String = "2023-01-01T11:10:00", count: Int = 108278887) {
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/casenotes/migration-id/.*")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            pageContent(
              objectMapper = objectMapper,
              content = listOf(
                CaseNoteMappingDto(
                  dpsCaseNoteId = UUID.randomUUID().toString(),
                  nomisCaseNoteId = 123456,
                  offenderNo = UUID.randomUUID().toString(),
                  nomisBookingId = 1,
                  mappingType = MIGRATED,
                  label = migrationId,
                  whenCreated = migrationId,
                ),
              ),
              pageSize = 1L,
              pageNumber = 0L,
              totalElements = count.toLong(),
              size = 1,
            ),
          ),
      ),
    )
  }

  fun stubGetCount(count: Long) {
    mappingApi.stubFor(
      post("/mapping/migration-id/{migrationId}/count-by-prisoner").willReturn(
        okJson(objectMapper.writeValueAsString(count)),
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
        jsonResponse(objectMapper.writeValueAsString(error), status.value()),
      ),
    )
  }

  fun stubUpdateMappingsByBookingId(response: List<CaseNoteMappingDto>) {
    mappingApi.stubFor(
      put(urlPathMatching("/mapping/casenotes/merge/booking-id/.+/to/.+")).willReturn(
        okJson(objectMapper.writeValueAsString(response)),
      ),
    )
  }

  fun stubUpdateMappingsByBookingIdError(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      put(urlPathMatching("/mapping/casenotes/merge/booking-id/.+/to/.+")).willReturn(
        jsonResponse(objectMapper.writeValueAsString(error), status.value()),
      ),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = mappingApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = mappingApi.verify(count, pattern)
  fun verify(count: CountMatchingStrategy, pattern: RequestPatternBuilder) = mappingApi.verify(count, pattern)
}
