package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.CountMatchingStrategy
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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.AlertMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.AlertMappingDto.MappingType.MIGRATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.pageContent
import java.time.LocalDateTime
import java.util.UUID

@Component
class AlertsMappingApiMockServer(private val objectMapper: ObjectMapper) {
  fun stubGetByNomisId(
    bookingId: Long = 123456,
    alertSequence: Long = 1,
    mapping: AlertMappingDto = AlertMappingDto(
      nomisBookingId = 123456,
      nomisAlertSequence = 1,
      dpsAlertId = UUID.randomUUID().toString(),
      offenderNo = "A1234KT",
      mappingType = MIGRATED,
    ),
  ) {
    mappingApi.stubFor(
      get(urlEqualTo("/mapping/alerts/nomis-booking-id/$bookingId/nomis-alert-sequence/$alertSequence")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(objectMapper.writeValueAsString(mapping)),
      ),
    )
  }

  fun stubGetByNomisId(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/alerts/nomis-booking-id/\\d+/nomis-alert-sequence/\\d+")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubGetByNomisId(
    bookingId: Long = 123456,
    alertSequence: Long = 1,
    status: HttpStatus,
    error: ErrorResponse = ErrorResponse(status = status.value()),
  ) {
    mappingApi.stubFor(
      get(urlEqualTo("/mapping/alerts/nomis-booking-id/$bookingId/nomis-alert-sequence/$alertSequence")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubPostMapping() {
    mappingApi.stubFor(
      post("/mapping/alerts").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(201),
      ),
    )
  }

  fun stubPostMapping(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      post("/mapping/alerts").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubPostMappingFailureFollowedBySuccess() {
    mappingApi.stubMappingCreateFailureFollowedBySuccess(url = "/mapping/alerts")
  }

  fun stubPostBatchMappings() {
    mappingApi.stubFor(
      post("/mapping/alerts/batch").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(201),
      ),
    )
  }

  fun stubPostBatchMappings(error: DuplicateMappingErrorResponse) {
    mappingApi.stubFor(
      post("/mapping/alerts/batch").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(409)
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubPostBatchMappingsFailureFollowedBySuccess() {
    mappingApi.stubMappingCreateFailureFollowedBySuccess(url = "/mapping/alerts/batch")
  }

  fun stubPostMappings(offenderNo: String) {
    mappingApi.stubFor(
      post("/mapping/alerts/$offenderNo/all").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(201),
      ),
    )
  }
  fun stubReplaceMappings(offenderNo: String) {
    mappingApi.stubFor(
      put("/mapping/alerts/$offenderNo/all").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(200),
      ),
    )
  }
  fun stubReplaceMappingsFailureFollowedBySuccess(offenderNo: String) {
    mappingApi.stubMappingUpdateFailureFollowedBySuccess(url = "/mapping/alerts/$offenderNo/all")
  }

  fun stubReplaceMappingsForMerge(offenderNo: String) {
    mappingApi.stubFor(
      put("/mapping/alerts/$offenderNo/merge").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(200),
      ),
    )
  }

  fun stubReplaceMappingsForMergeFollowedBySuccess(offenderNo: String) {
    mappingApi.stubMappingUpdateFailureFollowedBySuccess(url = "/mapping/alerts/$offenderNo/merge")
  }

  fun stubMigrationCount(recordsMigrated: Long) {
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/alerts/migration-id/.*/grouped-by-prisoner")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(200)
          .withBody(
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

  fun stubPostMappingsFailureFollowedBySuccess(offenderNo: String) = mappingApi.stubMappingCreateFailureFollowedBySuccess(url = "/mapping/alerts/$offenderNo/all")

  fun stubDeleteMapping() {
    mappingApi.stubFor(
      delete(urlPathMatching("/mapping/alerts/dps-alert-id/.*")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.NO_CONTENT.value()),
      ),
    )
  }

  fun stubDeleteMapping(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      delete(urlPathMatching("/mapping/alerts/dps-alert-id/.*")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubSingleItemByMigrationId(migrationId: String = "2020-01-01T11:10:00", count: Int = 278887) {
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/alerts/migration-id/.*")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            pageContent(
              objectMapper = objectMapper,
              content = listOf(
                AlertMappingDto(
                  dpsAlertId = UUID.randomUUID().toString(),
                  nomisBookingId = 123456,
                  nomisAlertSequence = 1,
                  mappingType = MIGRATED,
                  label = migrationId,
                  offenderNo = "A1234KT",
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

  fun verify(pattern: RequestPatternBuilder) = mappingApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = mappingApi.verify(count, pattern)
  fun verify(count: CountMatchingStrategy, pattern: RequestPatternBuilder) = mappingApi.verify(count, pattern)
}
