package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson

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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.pageContent
import java.time.LocalDateTime
import java.util.*

@Component
class ContactPersonMappingApiMockServer(private val objectMapper: ObjectMapper) {

  fun stubGetByNomisPersonIdOrNull(
    nomisPersonId: Long = 123456,
    mapping: PersonMappingDto? = PersonMappingDto(
      nomisId = 123456,
      dpsId = UUID.randomUUID().toString(),
      mappingType = PersonMappingDto.MappingType.MIGRATED,
    ),
  ) {
    mapping?.apply {
      mappingApi.stubFor(
        get(urlEqualTo("/mapping/contact-person/person/nomis-person-id/$nomisPersonId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(objectMapper.writeValueAsString(mapping)),
        ),
      )
    } ?: run {
      mappingApi.stubFor(
        get(urlEqualTo("/mapping/contact-person/person/nomis-person-id/$nomisPersonId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody(objectMapper.writeValueAsString(ErrorResponse(status = 404))),
        ),
      )
    }
  }

  fun stubCreateMappingsForMigration() {
    mappingApi.stubFor(
      post("/mapping/contact-person/migrate").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(201),
      ),
    )
  }

  fun stubCreateMappingsForMigrationFailureFollowedBySuccess() = mappingApi.stubMappingCreateFailureFollowedBySuccess(url = "/mapping/contact-person/migrate")

  fun stubCreateMappingsForMigration(error: DuplicateMappingErrorResponse) {
    mappingApi.stubFor(
      post("/mapping/contact-person/migrate").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(409)
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubGetMigrationDetails(migrationId: String = "2020-01-01T11:10:00", count: Int = 1) {
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/contact-person/person/migration-id/$migrationId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            pageContent(
              objectMapper = objectMapper,
              content = listOf(
                PersonMappingDto(
                  dpsId = UUID.randomUUID().toString(),
                  nomisId = 123456,
                  mappingType = PersonMappingDto.MappingType.MIGRATED,
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
  fun stubCreatePersonMapping() {
    mappingApi.stubFor(
      post("/mapping/contact-person/person").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(201),
      ),
    )
  }

  fun stubCreatePersonMapping(error: DuplicateMappingErrorResponse) {
    mappingApi.stubFor(
      post("/mapping/contact-person/person").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(409)
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreatePersonMappingFailureFollowedBySuccess() = mappingApi.stubMappingCreateFailureFollowedBySuccess("/mapping/contact-person/person")

  fun verify(pattern: RequestPatternBuilder) = mappingApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = mappingApi.verify(count, pattern)
  fun verify(count: CountMatchingStrategy, pattern: RequestPatternBuilder) = mappingApi.verify(count, pattern)
  fun resetAll() = mappingApi.resetAll()
}
