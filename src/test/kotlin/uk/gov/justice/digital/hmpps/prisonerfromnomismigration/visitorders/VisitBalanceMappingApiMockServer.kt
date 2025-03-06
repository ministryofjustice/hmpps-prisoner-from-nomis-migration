package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visitorders

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.CountMatchingStrategy
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.VisitBalanceMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.pageContent
import java.time.LocalDateTime
import java.util.*

@Component
class VisitBalanceMappingApiMockServer(private val objectMapper: ObjectMapper) {

  fun stubCreateMappingsForMigration() {
    mappingApi.stubFor(
      post("/mapping/visit-balance/migrate").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(201),
      ),
    )
  }

  fun stubCreateMappingsForMigrationFailureFollowedBySuccess() = mappingApi.stubMappingCreateFailureFollowedBySuccess(url = "/mapping/visit-balance/migrate")

  fun stubCreateMappingsForMigration(error: DuplicateMappingErrorResponse) {
    mappingApi.stubFor(
      post("/mapping/visit-balance/migrate").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(409)
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubGetMigrationDetails(migrationId: String = "2020-01-01T11:10:00", count: Int = 1) {
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/visit-balance/migration-id/$migrationId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            pageContent(
              objectMapper = objectMapper,
              content = listOf(
                VisitBalanceMappingDto(
                  dpsId = UUID.randomUUID().toString(),
                  nomisPrisonNumber = "A1234BC",
                  mappingType = VisitBalanceMappingDto.MappingType.MIGRATED,
                  label = migrationId,
                  whenCreated = LocalDateTime.now().toString(),
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

  fun stubGetByNomisPrisonNumberOrNull(
    nomisPrisonNumber: String = "A1234BC",
    mapping: VisitBalanceMappingDto? = VisitBalanceMappingDto(
      nomisPrisonNumber = nomisPrisonNumber,
      dpsId = UUID.randomUUID().toString(),
      mappingType = VisitBalanceMappingDto.MappingType.MIGRATED,
    ),
  ) {
    mapping?.apply {
      mappingApi.stubFor(
        get(urlEqualTo("/mapping/visit-balance/nomis-prison-number/$nomisPrisonNumber")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(objectMapper.writeValueAsString(mapping)),
        ),
      )
    } ?: run {
      mappingApi.stubFor(
        get(urlEqualTo("/mapping/visit-balance/nomis-prison-number/$nomisPrisonNumber")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody(objectMapper.writeValueAsString(ErrorResponse(status = 404))),
        ),
      )
    }
  }

  fun stubGetByNomisPrisonNumber(
    nomisPrisonNumber: String = "A1234BC",
    mapping: VisitBalanceMappingDto? = VisitBalanceMappingDto(
      nomisPrisonNumber = "A1234BC",
      dpsId = UUID.randomUUID().toString(),
      mappingType = VisitBalanceMappingDto.MappingType.MIGRATED,
    ),
  ) = stubGetByNomisPrisonNumberOrNull(nomisPrisonNumber, mapping)

  fun verify(pattern: RequestPatternBuilder) = mappingApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = mappingApi.verify(count, pattern)
  fun verify(count: CountMatchingStrategy, pattern: RequestPatternBuilder) = mappingApi.verify(count, pattern)
  fun resetAll() = mappingApi.resetAll()
}
