package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.CountMatchingStrategy
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock
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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CSIPChildMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CSIPFullMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CSIPReportMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.pageContent

@Component
class CSIPMappingApiMockServer(private val objectMapper: ObjectMapper) {
  companion object {
    const val CSIP_CREATE_MAPPING_URL = "/mapping/csip/all"
    const val CSIP_CREATE_CHILD_MAPPINGS_URL = "/mapping/csip/children/all"
    const val CSIP_GET_MAPPING_URL = "/mapping/csip/nomis-csip-id"
  }

  fun stubCSIPMappingByMigrationId(whenCreated: String = "2020-01-01T11:10:00", count: Int = 54327) {
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/csip/migration-id/.*")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            pageContent(
              objectMapper = objectMapper,
              content = listOf(
                CSIPFullMappingDto(
                  nomisCSIPReportId = 1234,
                  dpsCSIPReportId = "a1b2c3d4-e5f6-1234-5678-90a1b2c3d4e5",
                  mappingType = CSIPFullMappingDto.MappingType.MIGRATED,
                  label = "2022-02-14T09:58:45",
                  whenCreated = whenCreated,
                  attendeeMappings = listOf(),
                  factorMappings = listOf(),
                  interviewMappings = listOf(),
                  planMappings = listOf(),
                  reviewMappings = listOf(),
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

  fun stubPostMapping(url: String) {
    mappingApi.stubFor(
      post(urlEqualTo(url)).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.CREATED.value()),
      ),
    )
  }

  fun stubPostMapping(status: HttpStatus, url: String = CSIP_CREATE_MAPPING_URL, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      post(url).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubPostMappingFailureFollowedBySuccess(url: String = CSIP_CREATE_MAPPING_URL) {
    mappingApi.stubMappingCreateFailureFollowedBySuccess(url)
  }

  fun stubGetByNomisId(nomisCSIPId: Long = 1234, dpsCSIPId: String = "a1b2c3d4-e5f6-1234-5678-90a1b2c3d4e5") {
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/csip/nomis-csip-id/$nomisCSIPId"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(
              CSIPReportMappingDto(
                nomisCSIPReportId = nomisCSIPId,
                dpsCSIPReportId = dpsCSIPId,
                mappingType = CSIPReportMappingDto.MappingType.NOMIS_CREATED,
                label = "2022-02-14T09:58:45",
                whenCreated = "2020-01-01T11:10:00",
              ),
            ),
        ),
    )
  }
  fun stubGetByNomisId(status: HttpStatus) {
    stubGetErrorResponse(status = status, url = "/mapping/csip/nomis-csip-id/.*")
  }

  fun stubGetByMappingsNomisId(dpsCSIPId1: String, dpsCSIPId2: String) {
    mappingApi.stubFor(
      get(urlEqualTo("/mapping/csip/nomis-csip-id?nomisCSIPId=1234&nomisCSIPId=5678"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(
              listOf(
                CSIPReportMappingDto(
                  nomisCSIPReportId = 1234,
                  dpsCSIPReportId = dpsCSIPId1,
                  mappingType = CSIPReportMappingDto.MappingType.NOMIS_CREATED,
                  label = "2022-02-14T09:58:45",
                  whenCreated = "2020-01-01T11:10:00",
                ),
                CSIPReportMappingDto(
                  nomisCSIPReportId = 5678,
                  dpsCSIPReportId = dpsCSIPId2,
                  mappingType = CSIPReportMappingDto.MappingType.DPS_CREATED,
                  label = "2022-02-15T09:58:45",
                  whenCreated = "2020-01-01T11:10:00",
                ),
              ),
            ),
        ),
    )
  }
  fun stubGetByMappingsNomisId(status: HttpStatus) {
    stubGetErrorResponse(status = status, url = "/mapping/csip/nomis-csip-id?nomisCSIPId=1234&nomisCSIPId=5678")
  }

  fun stubGetFullMappingByDpsReportId(nomisCSIPId: Long = 1234, dpsCSIPId: String = "a1b2c3d4-e5f6-1234-5678-90a1b2c3d4e5") {
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/csip/dps-csip-id/$dpsCSIPId/all"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(
              CSIPFullMappingDto(
                nomisCSIPReportId = nomisCSIPId,
                dpsCSIPReportId = dpsCSIPId,
                mappingType = CSIPFullMappingDto.MappingType.NOMIS_CREATED,
                attendeeMappings = listOf(),
                factorMappings = listOf(),
                interviewMappings = listOf(),
                planMappings = listOf(),
                reviewMappings = listOf(),
                label = "2022-02-14T09:58:45",
                whenCreated = "2020-01-01T11:10:00",
              ),
            ),
        ),
    )
  }

  fun stubCreateChildMappings(nomisCSIPId: Long = 1234, dpsCSIPId: String = "a1b2c3d4-e5f6-1234-5678-90a1b2c3d4e5") {
    mappingApi.stubFor(
      put(urlPathMatching("/mapping/csip/children/all"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(
              CSIPFullMappingDto(
                nomisCSIPReportId = nomisCSIPId,
                dpsCSIPReportId = dpsCSIPId,
                mappingType = CSIPFullMappingDto.MappingType.NOMIS_CREATED,
                attendeeMappings = listOf(),
                factorMappings = listOf(),
                interviewMappings = listOf(),
                planMappings = listOf(),
                reviewMappings = listOf(),
                label = "2022-02-14T09:58:45",
                whenCreated = "2020-01-01T11:10:00",
              ),
            ),
        ),
    )
  }
  fun stubGetFullMappingByDpsReportId(status: HttpStatus) {
    stubGetErrorResponse(status = status, url = "/mapping/csip/dps-csip-id/\\S+/all")
  }

  fun stubCSIPLatestMigration(migrationId: String) {
    mappingApi.stubFor(
      get(urlEqualTo("/mapping/csip/migrated/latest")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(
            CSIPFullMappingDto(
              nomisCSIPReportId = 1234,
              dpsCSIPReportId = "a1b2c3d4-e5f6-1234-5678-90a1b2c3d4e5",
              mappingType = CSIPFullMappingDto.MappingType.MIGRATED,
              label = migrationId,
              whenCreated = "2020-01-01T11:10:00",
              attendeeMappings = listOf(),
              factorMappings = listOf(),
              interviewMappings = listOf(),
              planMappings = listOf(),
              reviewMappings = listOf(),
            ),
          ),
      ),
    )
  }

  fun verifyCreateCSIPFullMapping(dpsCSIPId: String, dpsCSIPFactorId: String, times: Int = 1) =
    verify(
      times,
      WireMock.postRequestedFor(WireMock.urlPathEqualTo(CSIP_CREATE_MAPPING_URL))
        .withRequestBody(
          WireMock.matchingJsonPath("dpsCSIPReportId", WireMock.equalTo(dpsCSIPId)),
        )
        .withRequestBody(
          WireMock.matchingJsonPath(
            "factorMappings[0].dpsId",
            WireMock.equalTo(dpsCSIPFactorId),
          ),
        ),
    )

  fun verifyCreateCSIPReportMapping(dpsCSIPId: String, times: Int = 1) =
    verify(
      times,
      WireMock.postRequestedFor(WireMock.urlPathEqualTo(CSIP_CREATE_MAPPING_URL)).withRequestBody(
        WireMock.matchingJsonPath(
          "dpsCSIPReportId",
          WireMock.equalTo(dpsCSIPId),
        ),
      ),
    )

  fun stubDeleteCSIPReportMapping(dpsCSIPId: String = "a1b2c3d4-e5f6-1234-5678-90a1b2c3d4e5") {
    mappingApi.stubFor(
      delete(urlEqualTo("/mapping/csip/dps-csip-id/$dpsCSIPId/all"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NO_CONTENT.value()),
        ),
    )
  }

  fun stubDeleteCSIPReportMapping(status: HttpStatus) {
    mappingApi.stubFor(
      delete(urlPathMatching("/mapping/csip/dps-csip-id/\\S+/all"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .withBody(objectMapper.writeValueAsString(status.value())),
        ),
    )
  }

  fun stubCSIPMappingCreateConflict(
    nomisCSIPId: Long = 1234,
    existingDPSCSIPId: String = "a1b2c3d4-e5f6-1234-5678-90a1b2c3d4e5",
    duplicateDPSCSIPId: String = "ddd596da-8eab-4d2a-a026-bc5afb8acda0",
  ) {
    mappingApi.stubFor(
      post(WireMock.urlPathEqualTo(CSIP_CREATE_MAPPING_URL))
        .willReturn(
          aResponse()
            .withStatus(409)
            .withHeader("Content-Type", "application/json")
            .withBody(
              """{
              "moreInfo": 
              {
                "existing" :  {
                  "nomisCSIPReportId": $nomisCSIPId,
                  "dpsCSIPReportId": "$existingDPSCSIPId",
                  "label": "2022-02-14T09:58:45",
                  "whenCreated": "2022-02-14T09:58:45",
                  "mappingType": "NOMIS_CREATED",
                  "attendeeMappings": [],
                  "factorMappings": [],
                  "interviewMappings": [],
                  "planMappings": [],
                  "reviewMappings": []
                },
                "duplicate" : {
                  "nomisCSIPReportId": $nomisCSIPId,
                  "dpsCSIPReportId": "$duplicateDPSCSIPId",
                  "label": "2022-02-14T09:58:45",
                  "whenCreated": "2022-02-14T09:58:45",
                  "mappingType": "NOMIS_CREATED",
                  "attendeeMappings": [],
                  "factorMappings": [],
                  "interviewMappings": [],
                  "planMappings": [],
                  "reviewMappings": []
                }
              }
              }""",
            ),
        ),
    )
  }

  // /////// CSIP Factor
  fun stubGetFactorByNomisId(nomisCSIPFactorId: Long, dpsCSIPFactorId: String, dpsCSIPReportId: String) {
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/csip/factors/nomis-csip-factor-id/$nomisCSIPFactorId"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(
              CSIPChildMappingDto(
                nomisId = nomisCSIPFactorId,
                dpsId = dpsCSIPFactorId,
                dpsCSIPReportId = dpsCSIPReportId,
                mappingType = CSIPChildMappingDto.MappingType.NOMIS_CREATED,
                label = "2022-02-14T09:58:45",
                whenCreated = "2020-01-01T11:10:00",
              ),
            ),
        ),
    )
  }

  fun stubGetFactorByNomisId(status: HttpStatus) =
    stubGetErrorResponse(status = status, url = "/mapping/csip/factors/nomis-csip-factor-id/.*")

  // /////// CSIP Plan
  fun stubGetPlanByNomisId(nomisCSIPPlanId: Long, dpsCSIPPlanId: String, dpsCSIPReportId: String) {
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/csip/plans/nomis-csip-plan-id/$nomisCSIPPlanId"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(
              CSIPChildMappingDto(
                nomisId = nomisCSIPPlanId,
                dpsId = dpsCSIPPlanId,
                dpsCSIPReportId = dpsCSIPReportId,
                mappingType = CSIPChildMappingDto.MappingType.NOMIS_CREATED,
                label = "2022-02-14T09:58:45",
                whenCreated = "2020-01-01T11:10:00",
              ),
            ),
        ),
    )
  }

  fun stubGetPlanByNomisId(status: HttpStatus) =
    stubGetErrorResponse(status = status, url = "/mapping/csip/plans/nomis-csip-plan-id/.*")

  // /////// CSIP Review
  fun stubGetReviewByNomisId(nomisCSIPReviewId: Long, dpsCSIPReviewId: String, dpsCSIPReportId: String) {
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/csip/reviews/nomis-csip-review-id/$nomisCSIPReviewId"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(
              CSIPChildMappingDto(
                nomisId = nomisCSIPReviewId,
                dpsId = dpsCSIPReviewId,
                dpsCSIPReportId = dpsCSIPReportId,
                mappingType = CSIPChildMappingDto.MappingType.NOMIS_CREATED,
                label = "2022-02-14T09:58:45",
                whenCreated = "2020-01-01T11:10:00",
              ),
            ),
        ),
    )
  }

  fun stubGetReviewByNomisId(status: HttpStatus) =
    stubGetErrorResponse(status = status, url = "/mapping/csip/reviews/nomis-csip-review-id/.*")

  // /////// CSIP Attendee
  fun stubGetAttendeeByNomisId(nomisCSIPAttendeeId: Long, dpsCSIPAttendeeId: String, dpsCSIPReportId: String) {
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/csip/attendees/nomis-csip-attendee-id/$nomisCSIPAttendeeId"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(
              CSIPChildMappingDto(
                nomisId = nomisCSIPAttendeeId,
                dpsId = dpsCSIPAttendeeId,
                dpsCSIPReportId = dpsCSIPReportId,
                mappingType = CSIPChildMappingDto.MappingType.NOMIS_CREATED,
                label = "2022-02-14T09:58:45",
                whenCreated = "2020-01-01T11:10:00",
              ),
            ),
        ),
    )
  }

  fun stubGetAttendeeByNomisId(status: HttpStatus) =
    stubGetErrorResponse(status = status, url = "/mapping/csip/attendees/nomis-csip-attendee-id/.*")

  // /////// CSIP Interview
  fun stubGetInterviewByNomisId(nomisCSIPInterviewId: Long, dpsCSIPInterviewId: String, dpsCSIPReportId: String) {
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/csip/interviews/nomis-csip-interview-id/$nomisCSIPInterviewId"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(
              CSIPChildMappingDto(
                nomisId = nomisCSIPInterviewId,
                dpsId = dpsCSIPInterviewId,
                dpsCSIPReportId = dpsCSIPReportId,
                mappingType = CSIPChildMappingDto.MappingType.NOMIS_CREATED,
                label = "2022-02-14T09:58:45",
                whenCreated = "2020-01-01T11:10:00",
              ),
            ),
        ),
    )
  }

  fun stubGetInterviewByNomisId(status: HttpStatus) =
    stubGetErrorResponse(status = status, url = "/mapping/csip/interviews/nomis-csip-interview-id/.*")

  // ////

  fun stubGetErrorResponse(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value()), url: String) {
    mappingApi.stubFor(
      get(urlPathMatching(url))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .withBody(objectMapper.writeValueAsString(error)),
        ),
    )
  }

  fun ResponseDefinitionBuilder.withBody(body: Any): ResponseDefinitionBuilder =
    this.withBody(objectMapper.writeValueAsString(body))

  fun verify(pattern: RequestPatternBuilder) = mappingApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = mappingApi.verify(count, pattern)
  fun verify(count: CountMatchingStrategy, pattern: RequestPatternBuilder) = mappingApi.verify(count, pattern)
}
