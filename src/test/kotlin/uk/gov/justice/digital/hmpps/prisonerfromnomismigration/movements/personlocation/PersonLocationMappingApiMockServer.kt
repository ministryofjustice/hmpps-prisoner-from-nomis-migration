package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.personlocation

import com.github.tomakehurst.wiremock.client.CountMatchingStrategy
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonLocationBookingMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonLocationMovementMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonLocationsMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import java.util.*

@Component
class PersonLocationMappingApiMockServer(private val jsonMapper: JsonMapper) {

  fun stubCreatePersonLocationMappings(
    status: HttpStatus = HttpStatus.CREATED,
    error: ErrorResponse = ErrorResponse(status = status.value()),
  ) {
    mappingApi.stubFor(
      put("/mapping/person-location/migrate")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .apply { if (status != HttpStatus.CREATED) withBody(jsonMapper.writeValueAsString(error)) },
        ),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = mappingApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = mappingApi.verify(count, pattern)
  fun verify(count: CountMatchingStrategy, pattern: RequestPatternBuilder) = mappingApi.verify(count, pattern)
}

fun personLocationsMapping(
  offenderNo: String = "A1234BC",
  bookingId: Long = 12345,
  dpsCustodialSeriesId: UUID = UUID.randomUUID(),
  nomisMovementSeq: Int = 1,
  dpsExternalMovementId: UUID = UUID.randomUUID(),
) = PersonLocationsMappingDto(
  offenderNo = offenderNo,
  migrationId = "2020-01-01T11:10:00",
  bookings = listOf(
    PersonLocationBookingMappingDto(
      bookingId = bookingId,
      dpsCustodialSeriesId = dpsCustodialSeriesId,
      movements = listOf(
        PersonLocationMovementMappingDto(
          nomisMovementSeq = nomisMovementSeq,
          dpsExternalMovementId = dpsExternalMovementId,
        ),
      ),
    ),
  ),
)
