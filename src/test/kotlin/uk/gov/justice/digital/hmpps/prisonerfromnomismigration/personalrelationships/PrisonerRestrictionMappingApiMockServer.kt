package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships

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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PrisonerRestrictionMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.objectMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.getRequestBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.pageContent
import java.time.LocalDateTime
import java.util.*

@Component
class PrisonerRestrictionMappingApiMockServer(private val objectMapper: ObjectMapper) {

  companion object {
    inline fun <reified T> getRequestBody(pattern: RequestPatternBuilder): T = mappingApi.getRequestBody(pattern, objectMapper = objectMapper)
  }

  fun stubGetByNomisPrisonerRestrictionIdOrNull(
    nomisPrisonerRestrictionId: Long = 123456,
    mapping: PrisonerRestrictionMappingDto? = PrisonerRestrictionMappingDto(
      offenderNo = "A1234KT",
      nomisId = nomisPrisonerRestrictionId,
      dpsId = UUID.randomUUID().toString(),
      mappingType = PrisonerRestrictionMappingDto.MappingType.MIGRATED,
    ),
  ) {
    mapping?.apply {
      mappingApi.stubFor(
        get(urlEqualTo("/mapping/contact-person/prisoner-restriction/nomis-prisoner-restriction-id/$nomisPrisonerRestrictionId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(objectMapper.writeValueAsString(mapping)),
        ),
      )
    } ?: run {
      mappingApi.stubFor(
        get(urlEqualTo("/mapping/contact-person/prisoner-restriction/nomis-prisoner-restriction-id/$nomisPrisonerRestrictionId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody(objectMapper.writeValueAsString(ErrorResponse(status = 404))),
        ),
      )
    }
  }

  fun stubCreateMappingForMigration() {
    mappingApi.stubFor(
      post("/mapping/contact-person/prisoner-restriction").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(201),
      ),
    )
  }

  @Suppress("unused")
  fun stubCreateMappingForMigrationFailureFollowedBySuccess() = mappingApi.stubMappingCreateFailureFollowedBySuccess(url = "/mapping/contact-person/prisoner-restriction")

  fun stubCreateMappingForMigration(error: DuplicateMappingErrorResponse) {
    mappingApi.stubFor(
      post("/mapping/contact-person/prisoner-restriction").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(409)
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubGetMigrationDetails(migrationId: String = "2020-01-01T11:10:00", count: Int = 1) {
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/contact-person/prisoner-restriction/migration-id/$migrationId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            pageContent(
              objectMapper = objectMapper,
              content = listOf(
                PrisonerRestrictionMappingDto(
                  dpsId = UUID.randomUUID().toString(),
                  nomisId = 123456,
                  offenderNo = "A1234KT",
                  mappingType = PrisonerRestrictionMappingDto.MappingType.MIGRATED,
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

  fun verify(pattern: RequestPatternBuilder) = mappingApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = mappingApi.verify(count, pattern)
  fun verify(count: CountMatchingStrategy, pattern: RequestPatternBuilder) = mappingApi.verify(count, pattern)
  fun resetAll() = mappingApi.resetAll()
}
