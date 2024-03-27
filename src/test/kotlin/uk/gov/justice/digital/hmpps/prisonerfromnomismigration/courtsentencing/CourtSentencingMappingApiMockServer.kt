package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.CountMatchingStrategy
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtCaseMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import java.util.UUID

@Component
class CourtSentencingMappingApiMockServer(private val objectMapper: ObjectMapper) {
  fun stubGetByNomisId(
    nomisCourtCaseId: Long = 123456,
    dpsCourtCaseId: String = UUID.randomUUID().toString(),
    mapping: CourtCaseMappingDto = CourtCaseMappingDto(
      nomisCourtCaseId = nomisCourtCaseId,
      dpsCourtCaseId = dpsCourtCaseId,
      mappingType = CourtCaseMappingDto.MappingType.MIGRATED,
    ),
  ) {
    mappingApi.stubFor(
      get(urlEqualTo("/mapping/court-sentencing/court-cases/nomis-court-case-id/$nomisCourtCaseId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(objectMapper.writeValueAsString(mapping)),
      ),
    )
  }

  fun stubGetByNomisId(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/court-sentencing/court-cases/nomis-court-case-id/\\d+")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubPostMapping() {
    mappingApi.stubFor(
      post("/mapping/court-sentencing/court-cases").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(201),
      ),
    )
  }

  fun stubPostMapping(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      post("/mapping/court-sentencing/court-cases").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubPostMappingFailureFollowedBySuccess() {
    mappingApi.stubMappingCreateFailureFollowedBySuccess(url = "/mapping/court-sentencing/court-cases")
  }

  fun stubCourtCaseMappingCreateConflict(
    existingDpsCourtCaseId: String = "10",
    duplicateDpsCourtCaseId: String = "11",
    nomisCourtCaseId: Long = 123,
  ) {
    mappingApi.stubFor(
      post(WireMock.urlPathEqualTo("/mapping/court-sentencing/court-cases"))
        .willReturn(
          aResponse()
            .withStatus(409)
            .withHeader("Content-Type", "application/json")
            .withBody(
              """{
              "moreInfo": 
              {
                "existing" :  {
                  "dpsCourtCaseId": "$existingDpsCourtCaseId",
                  "nomisCourtCaseId": $nomisCourtCaseId,
                  "label": "2022-02-14T09:58:45",
                  "whenCreated": "2022-02-14T09:58:45",
                  "mappingType": "MIGRATED"
                 },
                 "duplicate" : {
                  "dpsCourtCaseId": "$duplicateDpsCourtCaseId",
                  "nomisCourtCaseId": $nomisCourtCaseId,
                  "label": "2022-02-14T09:58:45",
                  "whenCreated": "2022-02-14T09:58:45",
                  "mappingType": "MIGRATED"
                  }
              }
              }""",
            ),
        ),
    )
  }

  fun createMappingCount(url: String) =
    mappingApi.findAll(WireMock.postRequestedFor(WireMock.urlPathEqualTo(url))).count()

  fun verify(pattern: RequestPatternBuilder) = mappingApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = mappingApi.verify(count, pattern)
  fun verify(count: CountMatchingStrategy, pattern: RequestPatternBuilder) = mappingApi.verify(count, pattern)
}
