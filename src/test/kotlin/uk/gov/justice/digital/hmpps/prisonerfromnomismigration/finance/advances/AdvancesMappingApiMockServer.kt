package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.advances

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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.AdvanceMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PageMetadata
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PagedModelAdvanceMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import java.time.LocalDateTime

@Component
class AdvancesMappingApiMockServer(private val jsonMapper: JsonMapper) {

  fun stubCreateMappingsForMigration() {
    mappingApi.stubFor(
      post("/mapping/advances").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(201),
      ),
    )
  }

  fun stubCreateMappingsForMigrationFailureFollowedBySuccess() = mappingApi.stubMappingCreateFailureFollowedBySuccess(url = "/mapping/advances")

  fun stubCreateMappingsForMigration(error: DuplicateMappingErrorResponse) {
    mappingApi.stubFor(
      post("/mapping/advances").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(409)
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubGetMigrationCount(migrationId: String = "2020-01-01T11:10:00", count: Int = 1) {
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/advances/migration-id/.*")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            jsonMapper.writeValueAsString(
              PagedModelAdvanceMappingDto(
                content = listOf(
                  AdvanceMappingDto(
                    dpsId = "A1234BC",
                    nomisAdvanceId = 12345L,
                    mappingType = AdvanceMappingDto.MappingType.MIGRATED,
                    label = migrationId,
                    whenCreated = LocalDateTime.now().toString(),
                  ),
                ),
                page = PageMetadata(
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

  fun stubGetAdvanceByNomisIdOrNull(
    nomisAdvanceId: Long = 12345,
    dpsId: String = "A1234BC",
    mapping: AdvanceMappingDto? = AdvanceMappingDto(
      nomisAdvanceId = nomisAdvanceId,
      dpsId = dpsId,
      mappingType = AdvanceMappingDto.MappingType.MIGRATED,
    ),
  ) {
    mapping?.apply {
      mappingApi.stubFor(
        get(urlEqualTo("/mapping/advances/nomis-id/$nomisAdvanceId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(jsonMapper.writeValueAsString(mapping)),
        ),
      )
    } ?: run {
      mappingApi.stubFor(
        get(urlEqualTo("/mapping/advances/nomis-id/$nomisAdvanceId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody(jsonMapper.writeValueAsString(ErrorResponse(status = 404))),
        ),
      )
    }
  }

  fun stubGetByNomisId(
    nomisAdvanceId: Long = 12345,
    mapping: AdvanceMappingDto? = AdvanceMappingDto(
      nomisAdvanceId = nomisAdvanceId,
      dpsId = "A1234BC",
      mappingType = AdvanceMappingDto.MappingType.MIGRATED,
    ),
  ) = stubGetAdvanceByNomisIdOrNull(nomisAdvanceId = nomisAdvanceId, mapping = mapping)

  fun verify(pattern: RequestPatternBuilder) = mappingApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = mappingApi.verify(count, pattern)
  fun verify(count: CountMatchingStrategy, pattern: RequestPatternBuilder) = mappingApi.verify(count, pattern)
  fun resetAll() = mappingApi.resetAll()
}
