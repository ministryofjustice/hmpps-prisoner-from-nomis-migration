package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits

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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.LocationMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.OfficialVisitMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.OfficialVisitorMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.VisitTimeSlotMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.pageContent
import java.time.LocalDateTime
import java.util.*

@Component
class OfficialVisitsMappingApiMockServer(private val jsonMapper: JsonMapper) {

  fun stubCreateMappingsForMigration() {
    mappingApi.stubFor(
      post("/mapping/official-visits").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(201),
      ),
    )
  }

  fun stubCreateMappingsForMigrationFailureFollowedBySuccess() = mappingApi.stubMappingCreateFailureFollowedBySuccess(url = "/mapping/official-visits")

  fun stubCreateMappingsForMigration(error: DuplicateMappingErrorResponse) {
    mappingApi.stubFor(
      post("/mapping/official-visits").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(409)
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }
  fun stubGetMigrationCount(migrationId: String = "2020-01-01T11:10:00", count: Int = 1) {
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/official-visits/migration-id/.*")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            pageContent(
              jsonMapper = jsonMapper,
              content = listOf(
                VisitTimeSlotMappingDto(
                  dpsId = "654321",
                  label = migrationId,
                  whenCreated = LocalDateTime.now().toString(),
                  nomisPrisonId = "WWI",
                  nomisDayOfWeek = "MON",
                  nomisSlotSequence = 2,
                  mappingType = VisitTimeSlotMappingDto.MappingType.MIGRATED,
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

  fun stubCreateVisitMapping() {
    mappingApi.stubFor(
      post("/mapping/official-visits/visit").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(201),
      ),
    )
  }

  fun stubCreateVisitMapping(error: DuplicateMappingErrorResponse) {
    mappingApi.stubFor(
      post("/mapping/official-visits/visit").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(409)
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateVisitMappingFailureFollowedBySuccess() = mappingApi.stubMappingCreateFailureFollowedBySuccess(url = "/mapping/official-visits/visit")

  fun stubGetByVisitNomisIdOrNull(
    nomisVisitId: Long = 1234L,
    mapping: OfficialVisitMappingDto? = OfficialVisitMappingDto(
      dpsId = "123456",
      nomisId = nomisVisitId,
      mappingType = OfficialVisitMappingDto.MappingType.MIGRATED,
    ),
  ) {
    mapping?.apply {
      mappingApi.stubFor(
        get(urlEqualTo("/mapping/official-visits/visit/nomis-id/$nomisVisitId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(jsonMapper.writeValueAsString(mapping)),
        ),
      )
    } ?: run {
      mappingApi.stubFor(
        get(urlEqualTo("/mapping/official-visits/visit/nomis-id/$nomisVisitId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody(jsonMapper.writeValueAsString(ErrorResponse(status = 404))),
        ),
      )
    }
  }
  fun stubGetByVisitNomisIdOrNullNotFoundFollowedBySuccess(
    nomisVisitId: Long = 1234L,
    mapping: OfficialVisitMappingDto? = OfficialVisitMappingDto(
      dpsId = "123456",
      nomisId = nomisVisitId,
      mappingType = OfficialVisitMappingDto.MappingType.MIGRATED,
    ),
  ) = mappingApi.stubMappingGetNotFoundFollowedBySuccess(url = "/mapping/official-visits/visit/nomis-id/$nomisVisitId", mapping = jsonMapper.writeValueAsString(mapping))

  fun stubGetByVisitNomisId(
    nomisVisitId: Long = 1234L,
    mapping: OfficialVisitMappingDto = OfficialVisitMappingDto(
      dpsId = "123456",
      nomisId = nomisVisitId,
      mappingType = OfficialVisitMappingDto.MappingType.MIGRATED,
    ),
  ) = stubGetByVisitNomisIdOrNull(nomisVisitId, mapping)

  fun stubDeleteByVisitNomisId(
    nomisVisitId: Long = 1234L,
  ) {
    mappingApi.stubFor(
      delete(urlEqualTo("/mapping/official-visits/visit/nomis-id/$nomisVisitId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.NO_CONTENT.value()),
      ),
    )
  }

  fun stubCreateVisitorMapping() {
    mappingApi.stubFor(
      post("/mapping/official-visits/visitor").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(201),
      ),
    )
  }

  fun stubCreateVisitorMapping(error: DuplicateMappingErrorResponse) {
    mappingApi.stubFor(
      post("/mapping/official-visits/visitor").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(409)
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateVisitorMappingFailureFollowedBySuccess() = mappingApi.stubMappingCreateFailureFollowedBySuccess(url = "/mapping/official-visits/visitor")

  fun stubGetByVisitorNomisIdOrNull(
    nomisVisitorId: Long = 1234L,
    mapping: OfficialVisitorMappingDto? = OfficialVisitorMappingDto(
      dpsId = "123456",
      nomisId = nomisVisitorId,
      mappingType = OfficialVisitorMappingDto.MappingType.MIGRATED,
    ),
  ) {
    mapping?.apply {
      mappingApi.stubFor(
        get(urlEqualTo("/mapping/official-visits/visitor/nomis-id/$nomisVisitorId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(jsonMapper.writeValueAsString(mapping)),
        ),
      )
    } ?: run {
      mappingApi.stubFor(
        get(urlEqualTo("/mapping/official-visits/visitor/nomis-id/$nomisVisitorId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody(jsonMapper.writeValueAsString(ErrorResponse(status = 404))),
        ),
      )
    }
  }

  fun stubGetByVisitorNomisId(
    nomisVisitorId: Long = 1234L,
    mapping: OfficialVisitorMappingDto = OfficialVisitorMappingDto(
      dpsId = "123456",
      nomisId = nomisVisitorId,
      mappingType = OfficialVisitorMappingDto.MappingType.MIGRATED,
    ),
  ) = stubGetByVisitorNomisIdOrNull(nomisVisitorId, mapping)

  fun stubDeleteByVisitorNomisId(
    nomisVisitorId: Long = 1234L,
  ) {
    mappingApi.stubFor(
      delete(urlEqualTo("/mapping/official-visits/visitor/nomis-id/$nomisVisitorId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.NO_CONTENT.value()),
      ),
    )
  }

  fun stubGetInternalLocationByNomisId(
    nomisLocationId: Long,
    mapping: LocationMappingDto = LocationMappingDto(
      dpsLocationId = UUID.randomUUID().toString(),
      nomisLocationId = nomisLocationId,
      mappingType = LocationMappingDto.MappingType.LOCATION_CREATED,
    ),
  ) {
    mappingApi.stubFor(
      get(urlEqualTo("/mapping/locations/nomis/$nomisLocationId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(jsonMapper.writeValueAsString(mapping)),
      ),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = mappingApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = mappingApi.verify(count, pattern)
  fun verify(count: CountMatchingStrategy, pattern: RequestPatternBuilder) = mappingApi.verify(count, pattern)
  fun resetAll() = mappingApi.resetAll()
}
