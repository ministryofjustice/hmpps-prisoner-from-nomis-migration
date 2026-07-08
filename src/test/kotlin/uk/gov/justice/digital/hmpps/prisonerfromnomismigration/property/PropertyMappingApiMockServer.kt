package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.property

import com.github.tomakehurst.wiremock.client.CountMatchingStrategy
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.delete
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.jsonResponse
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.ok
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.client.WireMock.status
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PropertyContainerMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PropertyContainerMappingDto.MappingType.MIGRATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import java.util.UUID

@Component
class PropertyMappingApiMockServer(private val jsonMapper: JsonMapper) {
  fun stubGetByNomisId(
    mapping: PropertyContainerMappingDto = PropertyContainerMappingDto(
      bookingId = 123456,
      nomisPropertyContainerId = 1234567,
      dpsPropertyContainerId = UUID.randomUUID().toString(),
      offenderNo = "A1234KT",
      mappingType = MIGRATED,
    ),
  ) {
    mappingApi.stubFor(
      get(urlEqualTo("/mapping/property/nomis-id/${mapping.nomisPropertyContainerId}")).willReturn(
        okJson(jsonMapper.writeValueAsString(mapping)),
      ),
    )
  }

  fun stubGetByNomisId(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/property/nomis-id/\\d+"))
        .willReturn(jsonResponse(error, status.value())),
    )
  }

  fun stubGetByNomisId(
    nomisPropertyContainerId: Long = 1234567,
    status: HttpStatus,
    error: ErrorResponse = ErrorResponse(status = status.value()),
  ) {
    mappingApi.stubFor(
      get(urlEqualTo("/mapping/property/nomis-id/$nomisPropertyContainerId")).willReturn(
        jsonResponse(error, status.value()),
      ),
    )
  }

  fun stubDeleteMapping() {
    mappingApi.stubFor(
      delete(urlPathMatching("/mapping/property/dps-id/.*")).willReturn(status(204)),
    )
  }

  fun stubGetByDpsId(dpsId: String, mappings: List<PropertyContainerMappingDto>) {
    mappingApi.stubFor(
      get("/mapping/property/dps-id/$dpsId").willReturn(
        okJson(jsonMapper.writeValueAsString(mappings)),
      ),
    )
  }

  fun stubDeleteMapping(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      delete(urlPathMatching("/mapping/property/dps-id/.*")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubPropertyMappingByMigrationId(whenCreated: String = "2020-01-01T11:10:00", count: Int = 278887) {
    val content = """{
      "dpsId": 191747,
      "nomisId": 123,
      "label": "2022-02-14T09:58:45",
      "whenCreated": "$whenCreated",
      "mappingType": "MIGRATED"
    }"""
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/property/migration-id/.*")).willReturn(
        okJson(pageContent(content, count)),
      ),
    )
  }

  fun stubPropertyMappingCreateConflict(
    existingDpsId: String,
    duplicateDpsId: String,
    nomisId: Long = 123,
  ) {
    mappingApi.stubFor(
      post(urlPathEqualTo("/mapping/property"))
        .willReturn(
          aResponse()
            .withStatus(409)
            .withHeader("Content-Type", "application/json")
            .withBody(
              """{
              "moreInfo": 
              {
                "existing" :  {
                  "dpsPropertyContainerId": "$existingDpsId",
                  "nomisPropertyContainerId": $nomisId,
                  "bookingId": 12345,
                  "offenderNo": "A1234AA",
                  "label": "2022-02-14T09:58:45",
                  "whenCreated": "2022-02-14T09:58:45",
                  "mappingType": "MIGRATED"
                },
                 "duplicate" : {
                  "dpsPropertyContainerId": "$duplicateDpsId",
                  "nomisPropertyContainerId": $nomisId,
                  "bookingId": 12345,
                  "offenderNo": "A1234AA",
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

  fun verifyCreateMappingPropertyIds(nomisIds: Array<String>, times: Int = 1) = nomisIds.forEach {
    verify(
      times,
      postRequestedFor(urlPathEqualTo("/mapping/property")).withRequestBody(
        matchingJsonPath(
          "dpsPropertyContainerId",
          equalTo(it),
        ),
      ),
    )
  }

  fun stubCountByMigrationId(count: Int) {
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/property/migration-id/.+/count")).willReturn(
        aResponse()
          .withStatus(HttpStatus.OK.value())
          .withHeader("Content-Type", "application/json")
          .withBody("$count"),
      ),
    )
  }

  fun stubNomisPropertyMappingFound(id: Long) {
    val content = """{
      "dpsPropertyContainerId": "191747",
      "nomisPropertyContainerId": $id,
      "bookingId": 12345,
      "offenderNo": "A1234AA",
      "label": "2022-02-14T09:58:45",
      "whenCreated": "2022-10-01T11:10:00",
      "mappingType": "MIGRATED"
    }"""
    mappingApi.stubFor(
      get(urlPathEqualTo("/mapping/property/nomis-id/$id"))
        .atPriority(1)
        .willReturn(okJson(content)),
    )
  }

  fun stubUpdateMappingsByNomisId() {
    mappingApi.stubFor(
      put(urlPathMatching("/mapping/property/merge/from/.+/to/.+")).willReturn(
        ok(),
      ),
    )
  }

  fun stubUpdateMappingsByNomisIdError(
    status: HttpStatus,
    error: ErrorResponse = ErrorResponse(status = status.value()),
  ) {
    mappingApi.stubFor(
      put(urlPathMatching("/mapping/property/merge/from/.+/to/.+")).willReturn(
        jsonResponse(jsonMapper.writeValueAsString(error), status.value()),
      ),
    )
  }

  fun stubUpdateMappingsByBookingId(response: List<PropertyContainerMappingDto>) {
    mappingApi.stubFor(
      put(urlPathMatching("/mapping/property/merge/booking-id/.+/to/.+")).willReturn(
        okJson(jsonMapper.writeValueAsString(response)),
      ),
    )
  }

  fun stubPostMapping() {
    mappingApi.stubFor(post("/mapping/property").willReturn(status(201)))
  }

  fun stubPostMapping(
    status: HttpStatus,
    error: ErrorResponse = ErrorResponse(status = status.value()),
  ) {
    mappingApi.stubFor(post("/mapping/property").willReturn(jsonResponse(error, status.value())))
  }

  fun stubPostMappingFailureFollowedBySuccess() {
    mappingApi.stubMappingCreateFailureFollowedBySuccess(url = "/mapping/property")
  }

  fun stubPostMapping(error: DuplicateMappingErrorResponse) {
    mappingApi.stubFor(post("/mapping/property").willReturn(jsonResponse(error, 409)))
  }

  fun verify(pattern: RequestPatternBuilder) = mappingApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = mappingApi.verify(count, pattern)
  fun verify(count: CountMatchingStrategy, pattern: RequestPatternBuilder) = mappingApi.verify(count, pattern)
}

fun pageContent(content: String, count: Int) =
  """
  {
      "content": [
        $content
      ],
      "pageable": {
          "sort": {
              "empty": true,
              "sorted": false,
              "unsorted": true
          },
          "offset": 0,
          "pageSize": 1,
          "pageNumber": 0,
          "paged": true,
          "unpaged": false
      },
      "last": false,
      "totalPages": 278887,
      "totalElements": $count,
      "size": 1,
      "number": 0,
      "sort": {
          "empty": true,
          "sorted": false,
          "unsorted": true
      },
      "first": true,
      "numberOfElements": 1,
      "empty": false
  }            
  """.trimIndent()
