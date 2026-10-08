package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.prisonerlocation

import com.github.tomakehurst.wiremock.client.CountMatchingStrategy
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PrisonerLocationBookingMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PrisonerLocationMovementMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PrisonerLocationsMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import java.util.*

@Component
class PrisonerLocationMappingApiMockServer(private val jsonMapper: JsonMapper) {

  fun stubCreatePrisonerLocationMappings() {
    mappingApi.stubFor(
      put("/mapping/prisoner-location/migrate")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(201),
        ),
    )
  }

  fun stubCreatePrisonerLocationMappings(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      put("/mapping/prisoner-location/migrate").willReturn(
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

fun prisonerLocationsMapping(
  offenderNo: String = "A1234BC",
  bookingId: Long = 12345,
  dpsCustodialSeriesId: UUID = UUID.randomUUID(),
  nomisMovementSeq: Int = 1,
  dpsExternalMovementId: UUID = UUID.randomUUID(),
) = PrisonerLocationsMappingDto(
  offenderNo = offenderNo,
  migrationId = "2020-01-01T11:10:00",
  bookings = listOf(
    PrisonerLocationBookingMappingDto(
      bookingId = bookingId,
      dpsCustodialSeriesId = dpsCustodialSeriesId,
      movements = listOf(
        PrisonerLocationMovementMappingDto(
          nomisMovementSeq = nomisMovementSeq,
          dpsExternalMovementId = dpsExternalMovementId,
        ),
      ),
    ),
  ),
)
