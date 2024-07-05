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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CSIPFactorMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CSIPMappingDto
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
                CSIPMappingDto(
                  nomisCSIPId = 1234,
                  dpsCSIPId = "a1b2c3d4-e5f6-1234-5678-90a1b2c3d4e5",
                  mappingType = CSIPMappingDto.MappingType.MIGRATED,
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
              CSIPMappingDto(
                nomisCSIPId = nomisCSIPId,
                dpsCSIPId = dpsCSIPId,
                mappingType = CSIPMappingDto.MappingType.NOMIS_CREATED,
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
            CSIPMappingDto(
              nomisCSIPId = 1234,
              dpsCSIPId = "a1b2c3d4-e5f6-1234-5678-90a1b2c3d4e5",
              mappingType = CSIPMappingDto.MappingType.MIGRATED,
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
          WireMock.equalTo("$dpsCSIPId"),
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
  fun stubGetFactorByNomisId(nomisCSIPFactorId: Long, dpsCSIPFactorId: String) {
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
                  "label": "2022-02-14T09:58:45",
                  "whenCreated": "2022-02-14T09:58:45",
                  "mappingType": "NOMIS_CREATED"
                 },
                 "duplicate" : {
                  "nomisCSIPFactorId": $nomisCSIPFactorId,
                  "dpsCSIPFactorId": "$duplicateDPSCSIPFactorId",
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
          WireMock.equalTo("$dpsCSIPFactorId"),
        ),
      ),
    )

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
