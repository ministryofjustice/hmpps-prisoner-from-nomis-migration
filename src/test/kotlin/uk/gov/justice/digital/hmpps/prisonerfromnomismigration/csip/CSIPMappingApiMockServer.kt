package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip

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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CSIPMappingDto
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
        // WireMock.okJson(mappingApi.pageContent(content, count)),
        // WireMock.okJson(
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

  fun stubGetCSIP(nomisCSIPId: Long = 1234) {
    val content = """{
      "dpsCSIPId": "a1b2c3d4-e5f6-1234-5678-90a1b2c3d4e5",
      "nomisCSIPId": $nomisCSIPId,
      "label": "2022-02-14T09:58:45",
      "whenCreated": "2020-01-01T11:10:00",
      "mappingType": "NOMIS_CREATED"
    }"""
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/csip/nomis-csip-id/$nomisCSIPId"))
        .willReturn(WireMock.okJson(content)),
    )
  }

  fun stubGetAnyCSIPNotFound() {
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/csip/nomis-csip-id/\\d"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value()),
        ),
    )
  }
  fun stubCSIPLatestMigration(migrationId: String) {
    mappingApi.stubFor(
      get(urlEqualTo("/mapping/csip/migrated/latest")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            """
            {
              "dpsCSIPId": "a1b2c3d4-e5f6-1234-5678-90a1b2c3d4e5",
              "nomisCSIPId": 1234,                                       
              "label": "$migrationId",
              "whenCreated": "2020-01-01T11:10:00",
              "mappingType": "MIGRATED"
            }              
            """,
          ),
      ),
    )
  }

  fun verifyCreateMappingCSIPId(dpsCSIPId: String, times: Int = 1) =
    verify(
      times,
      WireMock.postRequestedFor(WireMock.urlPathEqualTo("/mapping/csip")).withRequestBody(
        WireMock.matchingJsonPath(
          "dpsCSIPId",
          WireMock.equalTo("$dpsCSIPId"),
        ),
      ),
    )

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

  fun verify(pattern: RequestPatternBuilder) = mappingApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = mappingApi.verify(count, pattern)
  fun verify(count: CountMatchingStrategy, pattern: RequestPatternBuilder) = mappingApi.verify(count, pattern)
}
