package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents

import com.github.tomakehurst.wiremock.client.CountMatchingStrategy
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.delete
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.IncidentMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.pageContent

@Component
class IncidentsMappingApiMockServer(private val jsonMapper: JsonMapper) {

  companion object {
    const val INCIDENTS_CREATE_MAPPING_URL = "/mapping/incidents"
    const val INCIDENTS_GET_MAPPING_URL = "/mapping/incidents/nomis-incident-id"
  }

  fun verifyCreateMappingIncidentId(dpsIncidentId: String, times: Int = 1) = verify(
    times,
    postRequestedFor(WireMock.urlPathEqualTo("/mapping/incidents"))
      .withRequestBody(
        matchingJsonPath("dpsIncidentId", equalTo("$dpsIncidentId")),
      ),
  )

  fun stubIncidentMappingCreateConflict(
    nomisIncidentId: Long = 1234,
    existingDPSIncidentId: String = "fb4b2e91-91e7-457b-aa17-797f8c5c2f42",
    duplicateDPSIncidentId: String = "ddd596da-8eab-4d2a-a026-bc5afb8acda0",
  ) {
    mappingApi.stubFor(
      post(WireMock.urlPathEqualTo("/mapping/incidents"))
        .willReturn(
          aResponse()
            .withStatus(409)
            .withHeader("Content-Type", "application/json")
            .withBody(
              """{
              "moreInfo": 
              {
                "existing" :  {
                  "nomisIncidentId": $nomisIncidentId,
                  "dpsIncidentId": "$existingDPSIncidentId",
                  "label": "2022-02-14T09:58:45",
                  "whenCreated": "2022-02-14T09:58:45",
                  "mappingType": "NOMIS_CREATED"
                 },
                 "duplicate" : {
                  "nomisIncidentId": $nomisIncidentId,
                  "dpsIncidentId": "$duplicateDPSIncidentId",
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

  fun stubGetIncident(nomisIncidentId: Long = 1234, dpsIncidentId: String = "fb4b2e91-91e7-457b-aa17-797f8c5c2f42") {
    val content = """{
      "dpsIncidentId": "$dpsIncidentId",
      "nomisIncidentId": $nomisIncidentId,   
      "label": "2022-02-14T09:58:45",
      "whenCreated": "2020-01-01T11:10:00",
      "mappingType": "NOMIS_CREATED"
    }"""
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/incidents/nomis-incident-id/$nomisIncidentId"))
        .willReturn(WireMock.okJson(content)),
    )
  }

  fun stubGetAnyIncidentNotFound() {
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/incidents/nomis-incident-id/\\d"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value()),
        ),
    )
  }

  fun stubIncidentsLatestMigration(migrationId: String) {
    mappingApi.stubFor(
      get(urlEqualTo("/mapping/incidents/migrated/latest")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            """
            {
              "dpsIncidentId": "fb4b2e91-91e7-457b-aa17-797f8c5c2f42",
              "nomisIncidentId": 1234,                                       
              "label": "$migrationId",
              "whenCreated": "2020-01-01T11:10:00",
              "mappingType": "MIGRATED"
            }              
            """,
          ),
      ),
    )
  }

  fun stubIncidentMappingDelete(dpsIncidentId: String = "fb4b2e91-91e7-457b-aa17-797f8c5c2f42") {
    mappingApi.stubFor(
      delete(urlEqualTo("/mapping/incidents/dps-incident-id/$dpsIncidentId"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NO_CONTENT.value()),
        ),
    )
  }

  fun stubMappingCreate() {
    mappingApi.stubMappingCreate(INCIDENTS_CREATE_MAPPING_URL)
  }

  fun stubIncidentsMappingByMigrationId(whenCreated: String = "2020-01-01T11:10:00", count: Int = 54327) {
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/incidents/migration-id/.*")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            pageContent(
              jsonMapper = jsonMapper,
              content = listOf(
                IncidentMappingDto(
                  nomisIncidentId = 1234,
                  dpsIncidentId = "fb4b2e91-91e7-457b-aa17-797f8c5c2f42",
                  mappingType = IncidentMappingDto.MappingType.MIGRATED,
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

  fun verify(pattern: RequestPatternBuilder) = mappingApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = mappingApi.verify(count, pattern)
  fun verify(count: CountMatchingStrategy, pattern: RequestPatternBuilder) = mappingApi.verify(count, pattern)
}
