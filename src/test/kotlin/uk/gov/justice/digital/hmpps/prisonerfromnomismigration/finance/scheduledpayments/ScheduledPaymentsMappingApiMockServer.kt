package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.scheduledpayments

import com.github.tomakehurst.wiremock.client.CountMatchingStrategy
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PageMetadata
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ScheduledPaymentMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import java.time.LocalDateTime

@Component
class ScheduledPaymentsMappingApiMockServer(private val jsonMapper: JsonMapper) {

  fun stubCreateMapping() {
    mappingApi.stubFor(
      post("/mapping/scheduled-payments").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(201),
      ),
    )
  }

  fun stubCreateMappingFailureFollowedBySuccess() = mappingApi.stubMappingCreateFailureFollowedBySuccess(url = "/mapping/scheduled-payments")

  fun stubCreateMappingFailure(status: HttpStatus = HttpStatus.INTERNAL_SERVER_ERROR) {
    mappingApi.stubFor(
      post("/mapping/scheduled-payments").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(ErrorResponse(status = status.value()))),
      ),
    )
  }

  fun stubCreateMapping(error: DuplicateErrorResponse<ScheduledPaymentMappingDto>) {
    mappingApi.stubFor(
      post("/mapping/scheduled-payments").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(409)
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubGetMigrationCount(migrationId: String = "2020-01-01T11:10:00", count: Int = 1) {
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/scheduled-payments/migration-id/.*")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            jsonMapper.writeValueAsString(
              mapOf(
                "content" to listOf(
                  ScheduledPaymentMappingDto(
                    dpsId = "A1234BC",
                    nomisScheduledPaymentId = 12345L,
                    mappingType = ScheduledPaymentMappingDto.MappingType.MIGRATED,
                    label = migrationId,
                    whenCreated = LocalDateTime.now().toString(),
                  ),
                ),
                "page" to PageMetadata(
                  propertySize = 1,
                  number = 0,
                  totalPages = count.toLong(),
                  totalElements = count.toLong(),
                ),
              ),
            ),
          ),
      ),
    )
  }

  fun stubGetMigrationCountFailure(status: HttpStatus = HttpStatus.INTERNAL_SERVER_ERROR) {
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/scheduled-payments/migration-id/.*")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(ErrorResponse(status = status.value()))),
      ),
    )
  }

  fun stubGetByNomisIdFailure(
    nomisScheduledPaymentId: Long = 12345,
    status: HttpStatus = HttpStatus.INTERNAL_SERVER_ERROR,
  ) {
    mappingApi.stubFor(
      get(urlEqualTo("/mapping/scheduled-payments/nomis-id/$nomisScheduledPaymentId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(ErrorResponse(status = status.value()))),
      ),
    )
  }

  fun stubGetScheduledPaymentByNomisIdOrNull(
    nomisScheduledPaymentId: Long = 12345,
    dpsId: String = "A1234BC",
    mapping: ScheduledPaymentMappingDto? = ScheduledPaymentMappingDto(
      nomisScheduledPaymentId = nomisScheduledPaymentId,
      dpsId = dpsId,
      mappingType = ScheduledPaymentMappingDto.MappingType.MIGRATED,
    ),
  ) {
    mapping?.apply {
      mappingApi.stubFor(
        get(urlEqualTo("/mapping/scheduled-payments/nomis-id/$nomisScheduledPaymentId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(jsonMapper.writeValueAsString(mapping)),
        ),
      )
    } ?: run {
      mappingApi.stubFor(
        get(urlEqualTo("/mapping/scheduled-payments/nomis-id/$nomisScheduledPaymentId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody(jsonMapper.writeValueAsString(ErrorResponse(status = 404))),
        ),
      )
    }
  }

  fun stubGetByNomisId(
    nomisScheduledPaymentId: Long = 12345,
    mapping: ScheduledPaymentMappingDto? = ScheduledPaymentMappingDto(
      nomisScheduledPaymentId = nomisScheduledPaymentId,
      dpsId = "A1234BC",
      mappingType = ScheduledPaymentMappingDto.MappingType.MIGRATED,
    ),
  ) = stubGetScheduledPaymentByNomisIdOrNull(nomisScheduledPaymentId = nomisScheduledPaymentId, mapping = mapping)

  fun verify(pattern: RequestPatternBuilder) = mappingApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = mappingApi.verify(count, pattern)
  fun verify(count: CountMatchingStrategy, pattern: RequestPatternBuilder) = mappingApi.verify(count, pattern)
  fun resetAll() = mappingApi.resetAll()
}
