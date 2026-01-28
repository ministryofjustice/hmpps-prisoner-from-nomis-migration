package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits

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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.LocationMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.VisitSlotMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.VisitTimeSlotMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.pageContent
import java.time.LocalDateTime
import java.util.UUID

@Component
class VisitSlotsMappingApiMockServer(private val jsonMapper: JsonMapper) {

  fun stubCreateMappingsForMigration() {
    mappingApi.stubFor(
      post("/mapping/visit-slots").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(201),
      ),
    )
  }

  fun stubCreateMappingsForMigrationFailureFollowedBySuccess() = mappingApi.stubMappingCreateFailureFollowedBySuccess(url = "/mapping/visit-slots")

  fun stubCreateMappingsForMigration(error: DuplicateMappingErrorResponse) {
    mappingApi.stubFor(
      post("/mapping/visit-slots").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(409)
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }
  fun stubGetMigrationCount(migrationId: String = "2020-01-01T11:10:00", count: Int = 1) {
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/visit-slots/migration-id/.*")).willReturn(
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

  fun stubCreateTimeSlotMapping() {
    mappingApi.stubFor(
      post("/mapping/visit-slots/time-slots").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(201),
      ),
    )
  }

  fun stubCreateTimeSlotMappingFailureFollowedBySuccess() = mappingApi.stubMappingCreateFailureFollowedBySuccess(url = "/mapping/visit-slots/time-slots")

  fun stubCreateTimeSlotMapping(error: DuplicateMappingErrorResponse) {
    mappingApi.stubFor(
      post("/mapping/visit-slots/time-slots").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(409)
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubGetTimeSlotByNomisIdsOrNull(
    nomisPrisonId: String = "WWI",
    nomisDayOfWeek: String = "MON",
    nomisSlotSequence: Int = 2,
    mapping: VisitTimeSlotMappingDto? = VisitTimeSlotMappingDto(
      dpsId = "123456",
      mappingType = VisitTimeSlotMappingDto.MappingType.MIGRATED,
      nomisPrisonId = nomisPrisonId,
      nomisDayOfWeek = nomisDayOfWeek,
      nomisSlotSequence = nomisSlotSequence,
    ),
  ) {
    mapping?.apply {
      mappingApi.stubFor(
        get(urlEqualTo("/mapping/visit-slots/time-slots/nomis-prison-id/$nomisPrisonId/nomis-day-of-week/$nomisDayOfWeek/nomis-slot-sequence/$nomisSlotSequence")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(jsonMapper.writeValueAsString(mapping)),
        ),
      )
    } ?: run {
      mappingApi.stubFor(
        get(urlEqualTo("/mapping/visit-slots/time-slots/nomis-prison-id/$nomisPrisonId/nomis-day-of-week/$nomisDayOfWeek/nomis-slot-sequence/$nomisSlotSequence")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody(jsonMapper.writeValueAsString(ErrorResponse(status = 404))),
        ),
      )
    }
  }

  fun stubGetTimeSlotByNomisIds(
    nomisPrisonId: String = "WWI",
    nomisDayOfWeek: String = "MON",
    nomisSlotSequence: Int = 2,
    mapping: VisitTimeSlotMappingDto = VisitTimeSlotMappingDto(
      dpsId = "123456",
      mappingType = VisitTimeSlotMappingDto.MappingType.MIGRATED,
      nomisPrisonId = nomisPrisonId,
      nomisDayOfWeek = nomisDayOfWeek,
      nomisSlotSequence = nomisSlotSequence,
    ),
  ) = stubGetTimeSlotByNomisIdsOrNull(nomisPrisonId, nomisDayOfWeek, nomisSlotSequence, mapping)

  fun stubGetVisitSlotByNomisId(
    nomisId: Long = 123456,
    mapping: VisitSlotMappingDto = VisitSlotMappingDto(
      dpsId = "123456",
      mappingType = VisitSlotMappingDto.MappingType.MIGRATED,
      nomisId = nomisId,
    ),
  ) {
    mappingApi.stubFor(
      get(urlEqualTo("/mapping/visit-slots/visit-slot/nomis-id/$nomisId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(jsonMapper.writeValueAsString(mapping)),
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
