package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.CountMatchingStrategy
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.delete
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorporateMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.pageContent
import java.time.LocalDateTime

@Component
class OrganisationsMappingApiMockServer(private val objectMapper: ObjectMapper) {

  fun stubCreateMappingsForMigration() {
    mappingApi.stubFor(
      post("/mapping/corporate/migrate").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(201),
      ),
    )
  }

  @Suppress("unused")
  fun stubCreateMappingsForMigrationFailureFollowedBySuccess() = mappingApi.stubMappingCreateFailureFollowedBySuccess(url = "/mapping/corporate/migrate")

  fun stubCreateMappingsForMigration(error: DuplicateMappingErrorResponse) {
    mappingApi.stubFor(
      post("/mapping/corporate/migrate").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(409)
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }
  fun stubGetMigrationDetails(migrationId: String = "2020-01-01T11:10:00", count: Int = 1) {
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/corporate/organisation/migration-id/$migrationId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            pageContent(
              objectMapper = objectMapper,
              content = listOf(
                CorporateMappingDto(
                  dpsId = "654321",
                  nomisId = 123456,
                  mappingType = CorporateMappingDto.MappingType.MIGRATED,
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

  fun stubCreateCorporateMapping() {
    mappingApi.stubFor(
      post("/mapping/corporate/organisation").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(201),
      ),
    )
  }

  fun stubCreateCorporateMapping(error: DuplicateMappingErrorResponse) {
    mappingApi.stubFor(
      post("/mapping/corporate/organisation").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(409)
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubGetByNomisCorporateIdOrNull(
    nomisCorporateId: Long = 123456,
    mapping: CorporateMappingDto? = CorporateMappingDto(
      nomisId = 123456,
      dpsId = "123456",
      mappingType = CorporateMappingDto.MappingType.MIGRATED,
    ),
  ) {
    mapping?.apply {
      mappingApi.stubFor(
        get(urlEqualTo("/mapping/corporate/organisation/nomis-corporate-id/$nomisCorporateId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(objectMapper.writeValueAsString(mapping)),
        ),
      )
    } ?: run {
      mappingApi.stubFor(
        get(urlEqualTo("/mapping/corporate/organisation/nomis-corporate-id/$nomisCorporateId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody(objectMapper.writeValueAsString(ErrorResponse(status = 404))),
        ),
      )
    }
  }

  fun stubDeleteByNomisCorporateId(
    nomisCorporateId: Long = 123456,
  ) {
    mappingApi.stubFor(
      delete(urlEqualTo("/mapping/corporate/organisation/nomis-corporate-id/$nomisCorporateId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.NO_CONTENT.value()),
      ),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = mappingApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = mappingApi.verify(count, pattern)
  fun verify(count: CountMatchingStrategy, pattern: RequestPatternBuilder) = mappingApi.verify(count, pattern)
  fun resetAll() = mappingApi.resetAll()
}
