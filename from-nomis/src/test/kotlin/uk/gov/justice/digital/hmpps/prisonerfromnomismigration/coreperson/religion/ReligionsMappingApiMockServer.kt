package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.religion

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
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ReligionMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ReligionsMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.pageContent
import java.time.LocalDateTime

@Component
class ReligionsMappingApiMockServer(private val jsonMapper: JsonMapper) {

  fun stubCreateMappingsForMigration() {
    mappingApi.stubFor(
      post("/mapping/core-person-religion").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(201),
      ),
    )
  }

  fun stubCreateMappingsForMigrationFailureFollowedBySuccess() = mappingApi.stubMappingCreateFailureFollowedBySuccess(url = "/mapping/core-person-religion")

  fun stubCreateMappingsForMigration(error: DuplicateMappingErrorResponse) {
    mappingApi.stubFor(
      post("/mapping/core-person-religion").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(409)
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }
  fun stubGetMigrationCount(migrationId: String = "2020-01-01T11:10:00", count: Int = 1) {
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/core-person-religion/migration-id/.*")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            pageContent(
              jsonMapper = jsonMapper,
              content = listOf(
                ReligionsMappingDto(
                  cprId = "654321",
                  label = migrationId,
                  whenCreated = LocalDateTime.now().toString(),
                  nomisPrisonNumber = "A1234BC",
                  mappingType = ReligionsMappingDto.MappingType.MIGRATED,
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

  fun stubGetReligionsByNomisPrisonNumberOrNull(
    nomisPrisonNumber: String = "A1234BC",
    mapping: ReligionsMappingDto? = ReligionsMappingDto(
      cprId = "123456",
      mappingType = ReligionsMappingDto.MappingType.MIGRATED,
      nomisPrisonNumber = nomisPrisonNumber,
    ),
  ) {
    mapping?.apply {
      mappingApi.stubFor(
        get(urlEqualTo("/mapping/core-person-religion/religions/nomis-prison-number/$nomisPrisonNumber")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(jsonMapper.writeValueAsString(mapping)),
        ),
      )
    } ?: run {
      mappingApi.stubFor(
        get(urlEqualTo("/mapping/core-person-religion/religions/nomis-prison-number/$nomisPrisonNumber")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody(jsonMapper.writeValueAsString(ErrorResponse(status = 404))),
        ),
      )
    }
  }

  fun stubGetReligionsByNomisPrisonNumber(
    nomisPrisonNumber: String = "A1234BC",
    mapping: ReligionsMappingDto = ReligionsMappingDto(
      cprId = "123456",
      mappingType = ReligionsMappingDto.MappingType.MIGRATED,
      nomisPrisonNumber = nomisPrisonNumber,
    ),
  ) = stubGetReligionsByNomisPrisonNumberOrNull(nomisPrisonNumber, mapping)

  fun stubCreateReligionMapping() {
    mappingApi.stubFor(
      post("/mapping/core-person-religion/religion").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(201),
      ),
    )
  }

  fun stubCreateReligionMappingFailureFollowedBySuccess() = mappingApi.stubMappingCreateFailureFollowedBySuccess(url = "/mapping/core-person-religion/religion")

  fun stubCreateReligionMapping(error: DuplicateMappingErrorResponse) {
    mappingApi.stubFor(
      post("/mapping/core-person-religion/religion").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(409)
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubGetReligionByNomisIdOrNull(
    nomisId: Long = 123456,
    nomisPrisonNumber: String = "A1234BC",
    mapping: ReligionMappingDto? = ReligionMappingDto(
      cprId = "123456",
      mappingType = ReligionMappingDto.MappingType.MIGRATED,
      nomisId = nomisId,
      nomisPrisonNumber = nomisPrisonNumber,
    ),
  ) {
    mapping?.apply {
      mappingApi.stubFor(
        get(urlEqualTo("/mapping/core-person-religion/religion/nomis-id/$nomisId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(jsonMapper.writeValueAsString(mapping)),
        ),
      )
    } ?: run {
      mappingApi.stubFor(
        get(urlEqualTo("/mapping/core-person-religion/religion/nomis-id/$nomisId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody(jsonMapper.writeValueAsString(ErrorResponse(status = 404))),
        ),
      )
    }
  }
  fun stubGetReligionByNomisId(
    nomisId: Long = 123456,
    nomisPrisonNumber: String = "A1234BC",
    mapping: ReligionMappingDto = ReligionMappingDto(
      cprId = "123456",
      mappingType = ReligionMappingDto.MappingType.MIGRATED,
      nomisId = nomisId,
      nomisPrisonNumber = nomisPrisonNumber,
    ),
  ) = stubGetReligionByNomisIdOrNull(nomisId, nomisPrisonNumber, mapping)

  fun stubDeleteReligionByNomisId(
    nomisId: Long = 123456,
  ) {
    mappingApi.stubFor(
      delete(urlEqualTo("/mapping/core-person-religion/religion/nomis-id/$nomisId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.NO_CONTENT.value()),
      ),
    )
  }

  fun stubReplaceMappings() {
    mappingApi.stubFor(
      post("/mapping/core-person-religion/replace").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(200),
      ),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = mappingApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = mappingApi.verify(count, pattern)
  fun verify(count: CountMatchingStrategy, pattern: RequestPatternBuilder) = mappingApi.verify(count, pattern)
  fun resetAll() = mappingApi.resetAll()
}
