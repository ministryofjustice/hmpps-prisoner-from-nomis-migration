package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.CountMatchingStrategy
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.delete
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CSIPAttendeeMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CSIPFactorMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CSIPInterviewMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CSIPPlanMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CSIPReportMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CSIPReviewMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.pageContent

@Component
class CSIPMappingApiMockServer(private val objectMapper: ObjectMapper) {
  companion object {
    const val CSIP_CREATE_MAPPING_URL = "/mapping/csip"
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
                CSIPReportMappingDto(
                  nomisCSIPReportId = 1234,
                  dpsCSIPReportId = "a1b2c3d4-e5f6-1234-5678-90a1b2c3d4e5",
                  mappingType = CSIPReportMappingDto.MappingType.MIGRATED,
                  label = "2022-02-14T09:58:45",
                  whenCreated = whenCreated,
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

  fun stubCSIPLatestMigration(migrationId: String) {
    mappingApi.stubFor(
      get(urlEqualTo("/mapping/csip/migrated/latest")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(
            CSIPReportMappingDto(
              nomisCSIPReportId = 1234,
              dpsCSIPReportId = "a1b2c3d4-e5f6-1234-5678-90a1b2c3d4e5",
              mappingType = CSIPReportMappingDto.MappingType.MIGRATED,
              label = migrationId,
              whenCreated = "2020-01-01T11:10:00",
            ),
          ),
      ),
    )
  }

  fun verifyCreateCSIPReportMapping(dpsCSIPId: String, times: Int = 1) =
    verify(
      times,
      WireMock.postRequestedFor(WireMock.urlPathEqualTo("/mapping/csip")).withRequestBody(
        WireMock.matchingJsonPath(
          "dpsCSIPId",
          WireMock.equalTo(dpsCSIPId),
        ),
      ),
    )

  fun stubDeleteCSIPReportMapping(dpsCSIPId: String = "a1b2c3d4-e5f6-1234-5678-90a1b2c3d4e5") {
    stubDeleteMapping("/mapping/csip/dps-csip-id/$dpsCSIPId")
  }

  fun stubDeleteCSIPReportMapping(status: HttpStatus) {
    stubDeleteErrorResponse(status = status, url = "/mapping/csip/dps-csip-id/.*")
  }

  fun stubCSIPMappingCreateConflict(
    nomisCSIPId: Long = 1234,
    existingDPSCSIPId: String = "a1b2c3d4-e5f6-1234-5678-90a1b2c3d4e5",
    duplicateDPSCSIPId: String = "ddd596da-8eab-4d2a-a026-bc5afb8acda0",
  ) {
    mappingApi.stubFor(
      post(WireMock.urlPathEqualTo("/mapping/csip"))
        .willReturn(
          aResponse()
            .withStatus(409)
            .withHeader("Content-Type", "application/json")
            .withBody(
              """{
              "moreInfo": 
              {
                "existing" :  {
                  "nomisCSIPId": $nomisCSIPId,
                  "dpsCSIPId": "$existingDPSCSIPId",
                  "label": "2022-02-14T09:58:45",
                  "whenCreated": "2022-02-14T09:58:45",
                  "mappingType": "NOMIS_CREATED"
                },
                "duplicate" : {
                  "nomisCSIPId": $nomisCSIPId,
                  "dpsCSIPId": "$duplicateDPSCSIPId",
                  "label": "2022-02-14T09:58:45",
                  "whenCreated": "2022-02-14T09:58:45",
                  "mappingType": "NOMIS_CREATED"
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
              CSIPFactorMappingDto(
                nomisCSIPFactorId = nomisCSIPFactorId,
                dpsCSIPFactorId = dpsCSIPFactorId,
                dpsCSIPReportId = dpsCSIPReportId,
                mappingType = CSIPFactorMappingDto.MappingType.NOMIS_CREATED,
                label = "2022-02-14T09:58:45",
                whenCreated = "2020-01-01T11:10:00",
              ),
            ),
        ),
    )
  }

  fun stubGetFactorByNomisId(status: HttpStatus) =
    stubGetErrorResponse(status = status, url = "/mapping/csip/factors/nomis-csip-factor-id/.*")

  fun stubCSIPFactorMappingCreateConflict(
    nomisCSIPFactorId: Long,
    dpsCSIPReportId: String,
    existingDPSCSIPFactorId: String,
    duplicateDPSCSIPFactorId: String,
  ) {
    mappingApi.stubFor(
      post(WireMock.urlPathEqualTo("/mapping/csip/factors"))
        .willReturn(
          aResponse()
            .withStatus(409)
            .withHeader("Content-Type", "application/json")
            .withBody(
              """{
              "moreInfo": 
              {
                "existing" :  {
                  "nomisCSIPFactorId": $nomisCSIPFactorId,
                  "dpsCSIPFactorId": "$existingDPSCSIPFactorId",
                  "dpsCSIPReportId": "$dpsCSIPReportId",
                  "label": "2022-02-14T09:58:45",
                  "whenCreated": "2022-02-14T09:58:45",
                  "mappingType": "NOMIS_CREATED"
                 },
                 "duplicate" : {
                  "nomisCSIPFactorId": $nomisCSIPFactorId,
                  "dpsCSIPFactorId": "$duplicateDPSCSIPFactorId",
                  "dpsCSIPReportId": "$dpsCSIPReportId",
                  "label": "2022-02-14T09:58:45",
                  "whenCreated": "2022-02-14T09:58:45",
                  "mappingType": "NOMIS_CREATED"
                }
              }
              }""",
            ),
        ),
    )
  }

  fun stubCreateFactorMapping(status: HttpStatus) {
    stubPostErrorResponse(status = status, url = "/mapping/csip/factors")
  }
  fun stubDeleteFactorMapping(dpsCSIPFactorId: String = "a1b2c3d4-e5f6-1234-5678-90a1b2c3d4e5") {
    stubDeleteMapping(url = "/mapping/csip/factors/dps-csip-factor-id/$dpsCSIPFactorId")
  }
  fun stubDeleteFactorMapping(status: HttpStatus) {
    stubDeleteErrorResponse(status = status, url = "/mapping/csip/factors/dps-csip-factor-id/.*")
  }
  fun verifyCreateCSIPFactorMapping(dpsCSIPFactorId: String, times: Int = 1) =
    verify(
      times,
      WireMock.postRequestedFor(WireMock.urlPathEqualTo("/mapping/csip/factors")).withRequestBody(
        WireMock.matchingJsonPath(
          "dpsCSIPFactorId",
          WireMock.equalTo(dpsCSIPFactorId),
        ),
      ),
    )

  // /////// CSIP Plan
  fun stubGetPlanByNomisId(nomisCSIPPlanId: Long, dpsCSIPPlanId: String, dpsCSIPReportId: String) {
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/csip/plans/nomis-csip-plan-id/$nomisCSIPPlanId"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(
              CSIPPlanMappingDto(
                nomisCSIPPlanId = nomisCSIPPlanId,
                dpsCSIPPlanId = dpsCSIPPlanId,
                dpsCSIPReportId = dpsCSIPReportId,
                mappingType = CSIPPlanMappingDto.MappingType.NOMIS_CREATED,
                label = "2022-02-14T09:58:45",
                whenCreated = "2020-01-01T11:10:00",
              ),
            ),
        ),
    )
  }

  fun stubGetPlanByNomisId(status: HttpStatus) =
    stubGetErrorResponse(status = status, url = "/mapping/csip/plans/nomis-csip-plan-id/.*")

  fun stubCSIPPlanMappingCreateConflict(
    nomisCSIPPlanId: Long,
    existingDPSCSIPPlanId: String,
    duplicateDPSCSIPPlanId: String,
  ) {
    mappingApi.stubFor(
      post(WireMock.urlPathEqualTo("/mapping/csip/plans"))
        .willReturn(
          aResponse()
            .withStatus(409)
            .withHeader("Content-Type", "application/json")
            .withBody(
              """{
              "moreInfo": 
              {
                "existing" :  {
                  "nomisCSIPPlanId": $nomisCSIPPlanId,
                  "dpsCSIPPlanId": "$existingDPSCSIPPlanId",
                  "label": "2022-02-14T09:58:45",
                  "whenCreated": "2022-02-14T09:58:45",
                  "mappingType": "NOMIS_CREATED"
                 },
                 "duplicate" : {
                  "nomisCSIPPlanId": $nomisCSIPPlanId,
                  "dpsCSIPPlanId": "$duplicateDPSCSIPPlanId",
                  "label": "2022-02-14T09:58:45",
                  "whenCreated": "2022-02-14T09:58:45",
                  "mappingType": "NOMIS_CREATED"
                }
              }
              }""",
            ),
        ),
    )
  }

  fun stubCreatePlanMapping(status: HttpStatus) {
    stubPostErrorResponse(status = status, url = "/mapping/csip/plans")
  }
  fun stubDeletePlanMapping(dpsCSIPPlanId: String = "a1b2c3d4-e5f6-1234-5678-90a1b2c3d4e5") {
    stubDeleteMapping(url = "/mapping/csip/plans/dps-csip-plan-id/$dpsCSIPPlanId")
  }
  fun stubDeletePlanMapping(status: HttpStatus) {
    stubDeleteErrorResponse(status = status, url = "/mapping/csip/plans/dps-csip-plan-id/.*")
  }
  fun verifyCreateCSIPPlanMapping(dpsCSIPPlanId: String, times: Int = 1) =
    verify(
      times,
      WireMock.postRequestedFor(WireMock.urlPathEqualTo("/mapping/csip/plans")).withRequestBody(
        WireMock.matchingJsonPath(
          "dpsCSIPPlanId",
          WireMock.equalTo(dpsCSIPPlanId),
        ),
      ),
    )

  // /////// CSIP Review
  fun stubGetReviewByNomisId(nomisCSIPReviewId: Long, dpsCSIPReviewId: String, dpsCSIPReportId: String) {
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/csip/reviews/nomis-csip-review-id/$nomisCSIPReviewId"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(
              CSIPReviewMappingDto(
                nomisCSIPReviewId = nomisCSIPReviewId,
                dpsCSIPReviewId = dpsCSIPReviewId,
                dpsCSIPReportId = dpsCSIPReportId,
                mappingType = CSIPReviewMappingDto.MappingType.NOMIS_CREATED,
                label = "2022-02-14T09:58:45",
                whenCreated = "2020-01-01T11:10:00",
              ),
            ),
        ),
    )
  }

  fun stubGetReviewByNomisId(status: HttpStatus) =
    stubGetErrorResponse(status = status, url = "/mapping/csip/reviews/nomis-csip-review-id/.*")

  fun stubCSIPReviewMappingCreateConflict(
    nomisCSIPReviewId: Long,
    existingDPSCSIPReviewId: String,
    duplicateDPSCSIPReviewId: String,
  ) {
    mappingApi.stubFor(
      post(WireMock.urlPathEqualTo("/mapping/csip/reviews"))
        .willReturn(
          aResponse()
            .withStatus(409)
            .withHeader("Content-Type", "application/json")
            .withBody(
              """{
              "moreInfo": 
              {
                "existing" :  {
                  "nomisCSIPReviewId": $nomisCSIPReviewId,
                  "dpsCSIPReviewId": "$existingDPSCSIPReviewId",
                  "label": "2022-02-14T09:58:45",
                  "whenCreated": "2022-02-14T09:58:45",
                  "mappingType": "NOMIS_CREATED"
                 },
                 "duplicate" : {
                  "nomisCSIPReviewId": $nomisCSIPReviewId,
                  "dpsCSIPReviewId": "$duplicateDPSCSIPReviewId",
                  "label": "2022-02-14T09:58:45",
                  "whenCreated": "2022-02-14T09:58:45",
                  "mappingType": "NOMIS_CREATED"
                }
              }
              }""",
            ),
        ),
    )
  }

  fun stubCreateReviewMapping(status: HttpStatus) {
    stubPostErrorResponse(status = status, url = "/mapping/csip/reviews")
  }
  fun stubDeleteReviewMapping(dpsCSIPReviewId: String = "a1b2c3d4-e5f6-1234-5678-90a1b2c3d4e5") {
    stubDeleteMapping(url = "/mapping/csip/reviews/dps-csip-review-id/$dpsCSIPReviewId")
  }
  fun stubDeleteReviewMapping(status: HttpStatus) {
    stubDeleteErrorResponse(status = status, url = "/mapping/csip/reviews/dps-csip-review-id/.*")
  }
  fun verifyCreateCSIPReviewMapping(dpsCSIPReviewId: String, times: Int = 1) =
    verify(
      times,
      WireMock.postRequestedFor(WireMock.urlPathEqualTo("/mapping/csip/reviews")).withRequestBody(
        WireMock.matchingJsonPath(
          "dpsCSIPReviewId",
          WireMock.equalTo(dpsCSIPReviewId),
        ),
      ),
    )

  // /////// CSIP Attendee
  fun stubGetAttendeeByNomisId(nomisCSIPAttendeeId: Long, dpsCSIPAttendeeId: String, dpsCSIPReportId: String) {
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/csip/attendees/nomis-csip-attendee-id/$nomisCSIPAttendeeId"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(
              CSIPAttendeeMappingDto(
                nomisCSIPAttendeeId = nomisCSIPAttendeeId,
                dpsCSIPAttendeeId = dpsCSIPAttendeeId,
                dpsCSIPReportId = dpsCSIPReportId,
                mappingType = CSIPAttendeeMappingDto.MappingType.NOMIS_CREATED,
                label = "2022-02-14T09:58:45",
                whenCreated = "2020-01-01T11:10:00",
              ),
            ),
        ),
    )
  }

  fun stubGetAttendeeByNomisId(status: HttpStatus) =
    stubGetErrorResponse(status = status, url = "/mapping/csip/attendees/nomis-csip-attendee-id/.*")

  fun stubCSIPAttendeeMappingCreateConflict(
    nomisCSIPAttendeeId: Long,
    existingDPSCSIPAttendeeId: String,
    duplicateDPSCSIPAttendeeId: String,
  ) {
    mappingApi.stubFor(
      post(WireMock.urlPathEqualTo("/mapping/csip/attendees"))
        .willReturn(
          aResponse()
            .withStatus(409)
            .withHeader("Content-Type", "application/json")
            .withBody(
              """{
              "moreInfo": 
              {
                "existing" :  {
                  "nomisCSIPAttendeeId": $nomisCSIPAttendeeId,
                  "dpsCSIPAttendeeId": "$existingDPSCSIPAttendeeId",
                  "label": "2022-02-14T09:58:45",
                  "whenCreated": "2022-02-14T09:58:45",
                  "mappingType": "NOMIS_CREATED"
                 },
                 "duplicate" : {
                  "nomisCSIPAttendeeId": $nomisCSIPAttendeeId,
                  "dpsCSIPAttendeeId": "$duplicateDPSCSIPAttendeeId",
                  "label": "2022-02-14T09:58:45",
                  "whenCreated": "2022-02-14T09:58:45",
                  "mappingType": "NOMIS_CREATED"
                }
              }
              }""",
            ),
        ),
    )
  }

  fun stubCreateAttendeeMapping(status: HttpStatus) {
    stubPostErrorResponse(status = status, url = "/mapping/csip/attendees")
  }
  fun stubDeleteAttendeeMapping(dpsCSIPAttendeeId: String = "a1b2c3d4-e5f6-1234-5678-90a1b2c3d4e5") {
    stubDeleteMapping(url = "/mapping/csip/attendees/dps-csip-attendee-id/$dpsCSIPAttendeeId")
  }
  fun stubDeleteAttendeeMapping(status: HttpStatus) {
    stubDeleteErrorResponse(status = status, url = "/mapping/csip/attendees/dps-csip-attendee-id/.*")
  }
  fun verifyCreateCSIPAttendeeMapping(dpsCSIPAttendeeId: String, times: Int = 1) =
    verify(
      times,
      WireMock.postRequestedFor(WireMock.urlPathEqualTo("/mapping/csip/attendees")).withRequestBody(
        WireMock.matchingJsonPath(
          "dpsCSIPAttendeeId",
          WireMock.equalTo(dpsCSIPAttendeeId),
        ),
      ),
    )

  // /////// CSIP Interview
  fun stubGetInterviewByNomisId(nomisCSIPInterviewId: Long, dpsCSIPInterviewId: String, dpsCSIPReportId: String) {
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/csip/interviews/nomis-csip-interview-id/$nomisCSIPInterviewId"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(
              CSIPInterviewMappingDto(
                nomisCSIPInterviewId = nomisCSIPInterviewId,
                dpsCSIPInterviewId = dpsCSIPInterviewId,
                dpsCSIPReportId = dpsCSIPReportId,
                mappingType = CSIPInterviewMappingDto.MappingType.NOMIS_CREATED,
                label = "2022-02-14T09:58:45",
                whenCreated = "2020-01-01T11:10:00",
              ),
            ),
        ),
    )
  }

  fun stubGetInterviewByNomisId(status: HttpStatus) =
    stubGetErrorResponse(status = status, url = "/mapping/csip/interviews/nomis-csip-interview-id/.*")

  fun stubCSIPInterviewMappingCreateConflict(
    nomisCSIPInterviewId: Long,
    existingDPSCSIPInterviewId: String,
    duplicateDPSCSIPInterviewId: String,
  ) {
    mappingApi.stubFor(
      post(WireMock.urlPathEqualTo("/mapping/csip/interviews"))
        .willReturn(
          aResponse()
            .withStatus(409)
            .withHeader("Content-Type", "application/json")
            .withBody(
              """{
              "moreInfo": 
              {
                "existing" :  {
                  "nomisCSIPInterviewId": $nomisCSIPInterviewId,
                  "dpsCSIPInterviewId": "$existingDPSCSIPInterviewId",
                  "label": "2022-02-14T09:58:45",
                  "whenCreated": "2022-02-14T09:58:45",
                  "mappingType": "NOMIS_CREATED"
                 },
                 "duplicate" : {
                  "nomisCSIPInterviewId": $nomisCSIPInterviewId,
                  "dpsCSIPInterviewId": "$duplicateDPSCSIPInterviewId",
                  "label": "2022-02-14T09:58:45",
                  "whenCreated": "2022-02-14T09:58:45",
                  "mappingType": "NOMIS_CREATED"
                }
              }
              }""",
            ),
        ),
    )
  }

  fun stubCreateInterviewMapping(status: HttpStatus) {
    stubPostErrorResponse(status = status, url = "/mapping/csip/interviews")
  }
  fun stubDeleteInterviewMapping(dpsCSIPInterviewId: String = "a1b2c3d4-e5f6-1234-5678-90a1b2c3d4e5") {
    stubDeleteMapping(url = "/mapping/csip/interviews/dps-csip-interview-id/$dpsCSIPInterviewId")
  }
  fun stubDeleteInterviewMapping(status: HttpStatus) {
    stubDeleteErrorResponse(status = status, url = "/mapping/csip/interviews/dps-csip-interview-id/.*")
  }
  fun verifyCreateCSIPInterviewMapping(dpsCSIPInterviewId: String, times: Int = 1) =
    verify(
      times,
      WireMock.postRequestedFor(WireMock.urlPathEqualTo("/mapping/csip/interviews")).withRequestBody(
        WireMock.matchingJsonPath(
          "dpsCSIPInterviewId",
          WireMock.equalTo(dpsCSIPInterviewId),
        ),
      ),
    )

  // ////

  fun stubPostErrorResponse(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value()), url: String) {
    mappingApi.stubFor(
      post(url).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubDeleteMapping(url: String) {
    mappingApi.stubFor(
      delete(urlEqualTo(url))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NO_CONTENT.value()),
        ),
    )
  }

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
  fun stubDeleteErrorResponse(status: HttpStatus, url: String, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      delete(urlPathMatching(url))
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
