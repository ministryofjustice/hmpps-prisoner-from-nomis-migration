package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.court

import com.github.tomakehurst.wiremock.client.CountMatchingStrategy
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtMovementMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtScheduleMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.jsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.getRequestBody
import java.util.*

@Component
class CourtSchedulerMappingApiMockServer(private val jsonMapper: JsonMapper) {
  companion object {
    inline fun <reified T> getRequestBody(pattern: RequestPatternBuilder): T = mappingApi.getRequestBody(pattern, jsonMapper = jsonMapper)
  }

  fun stubCreateCourtScheduleMapping() {
    mappingApi.stubFor(
      post("/mapping/court/schedule")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(201),
        ),
    )
  }

  fun stubCreateCourtScheduleMapping(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      post("/mapping/court/schedule").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateCourtScheduleMappingConflict(error: DuplicateMappingErrorResponse) {
    mappingApi.stubFor(
      post("/mapping/court/schedule").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(409)
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateCourtScheduleMappingFailureFollowedBySuccess() = mappingApi.stubMappingCreateFailureFollowedBySuccess("/mapping/court/schedule")

  fun stubGetCourtScheduleMapping(nomisEventId: Long = 1L, dpsCourtAppearanceId: UUID = UUID.randomUUID()) {
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/court/schedule/nomis-id/$nomisEventId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            jsonMapper.writeValueAsString(
              courtScheduleMapping(
                nomisEventId = nomisEventId,
                dpsCourtAppearanceId = dpsCourtAppearanceId,
              ),
            ),
          ),
      ),
    )
  }

  fun stubGetCourtScheduleMapping(nomisEventId: Long = 1L, status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/court/schedule/nomis-id/$nomisEventId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateCourtMovementMapping() {
    mappingApi.stubFor(
      post("/mapping/court/movement")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(201),
        ),
    )
  }

  fun stubCreateCourtMovementMapping(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      post("/mapping/court/movement").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateCourtMovementMappingConflict(error: DuplicateMappingErrorResponse) {
    mappingApi.stubFor(
      post("/mapping/court/movement").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(409)
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateCourtMovementMappingFailureFollowedBySuccess() = mappingApi.stubMappingCreateFailureFollowedBySuccess("/mapping/court/movement")

  fun stubGetCourtMovementMapping(nomisBookingId: Long = 12345L, nomisMovementSeq: Int = 3, dpsCourtMovementId: UUID = UUID.randomUUID()) {
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/court/movement/nomis-id/$nomisBookingId/$nomisMovementSeq")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            jsonMapper.writeValueAsString(
              courtMovementMapping(
                nomisBookingId = nomisBookingId,
                nomisMovementSeq = nomisMovementSeq,
                dpsCourtMovementId = dpsCourtMovementId,
              ),
            ),
          ),
      ),
    )
  }

  fun stubGetCourtMovementMapping(nomisBookingId: Long = 12345L, nomisMovementSeq: Int = 3, status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/court/movement/nomis-id/$nomisBookingId/$nomisMovementSeq")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = mappingApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = mappingApi.verify(count, pattern)
  fun verify(count: CountMatchingStrategy, pattern: RequestPatternBuilder) = mappingApi.verify(count, pattern)
}

fun courtScheduleMapping(
  nomisEventId: Long = 1L,
  prisonerNumber: String = "A1234BC",
  dpsCourtAppearanceId: UUID = UUID.randomUUID(),
) = CourtScheduleMappingDto(
  prisonerNumber = prisonerNumber,
  bookingId = 12345,
  nomisEventId = nomisEventId,
  dpsCourtAppearanceId = dpsCourtAppearanceId,
  mappingType = CourtScheduleMappingDto.MappingType.MIGRATED,
)

fun courtMovementMapping(
  nomisBookingId: Long = 12345L,
  nomisMovementSeq: Int = 3,
  prisonerNumber: String = "A1234BC",
  dpsCourtMovementId: UUID = UUID.randomUUID(),
) = CourtMovementMappingDto(
  prisonerNumber = prisonerNumber,
  nomisBookingId = nomisBookingId,
  nomisMovementSeq = nomisMovementSeq,
  dpsCourtMovementId = dpsCourtMovementId,
  mappingType = CourtMovementMappingDto.MappingType.MIGRATED,
)
