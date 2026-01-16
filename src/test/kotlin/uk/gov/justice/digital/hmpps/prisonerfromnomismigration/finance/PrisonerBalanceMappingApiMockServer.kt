package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance

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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PageMetadata
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PagedModelPrisonerBalanceMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PrisonerBalanceMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import java.time.LocalDateTime

@Component
class PrisonerBalanceMappingApiMockServer(private val objectMapper: ObjectMapper) {

  fun stubCreateMappingsForMigration() {
    mappingApi.stubFor(
      post("/mapping/prisoner-balance").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(201),
      ),
    )
  }

  fun stubCreateMappingsForMigrationFailureFollowedBySuccess() = mappingApi.stubMappingCreateFailureFollowedBySuccess(url = "/mapping/prisoner-balance")

  fun stubCreateMappingsForMigration(error: DuplicateMappingErrorResponse) {
    mappingApi.stubFor(
      post("/mapping/prisoner-balance").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(409)
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubGetMigrationDetails(migrationId: String = "2020-01-01T11:10:00", count: Int = 1) {
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/prisoner-balance/migration-id/$migrationId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            objectMapper.writeValueAsString(
              PagedModelPrisonerBalanceMappingDto(
                content = listOf(
                  PrisonerBalanceMappingDto(
                    dpsId = "A1234BC",
                    nomisRootOffenderId = 12345L,
                    mappingType = PrisonerBalanceMappingDto.MappingType.MIGRATED,
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

  fun stubGetPrisonerBalanceByNomisIdOrNull(
    nomisRootOffenderId: Long = 12345,
    dpsId: String = "A1234BC",
    mapping: PrisonerBalanceMappingDto? = PrisonerBalanceMappingDto(
      nomisRootOffenderId = nomisRootOffenderId,
      dpsId = dpsId,
      mappingType = PrisonerBalanceMappingDto.MappingType.MIGRATED,
    ),
  ) {
    mapping?.apply {
      mappingApi.stubFor(
        get(urlEqualTo("/mapping/prisoner-balance/nomis-id/$nomisRootOffenderId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(objectMapper.writeValueAsString(mapping)),
        ),
      )
    } ?: run {
      mappingApi.stubFor(
        get(urlEqualTo("/mapping/prisoner-balance/nomis-id/$nomisRootOffenderId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody(objectMapper.writeValueAsString(ErrorResponse(status = 404))),
        ),
      )
    }
  }

  fun stubGetByNomisId(
    nomisRootOffenderId: Long = 12345,
    mapping: PrisonerBalanceMappingDto? = PrisonerBalanceMappingDto(
      nomisRootOffenderId = nomisRootOffenderId,
      dpsId = "A1234BC",
      mappingType = PrisonerBalanceMappingDto.MappingType.MIGRATED,
    ),
  ) = stubGetPrisonerBalanceByNomisIdOrNull(nomisRootOffenderId = nomisRootOffenderId, mapping = mapping)

  fun verify(pattern: RequestPatternBuilder) = mappingApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = mappingApi.verify(count, pattern)
  fun verify(count: CountMatchingStrategy, pattern: RequestPatternBuilder) = mappingApi.verify(count, pattern)
  fun resetAll() = mappingApi.resetAll()
}
