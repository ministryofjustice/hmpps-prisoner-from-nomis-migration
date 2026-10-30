package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.personlocation

import com.github.tomakehurst.wiremock.client.CountMatchingStrategy
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.delete
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import com.github.tomakehurst.wiremock.stubbing.Scenario
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonLocationBookingIdMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonLocationBookingMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonLocationMovementIdMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonLocationMovementMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonLocationsMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.jsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.getRequestBody
import java.util.*

@Component
class PersonLocationMappingApiMockServer(private val jsonMapper: JsonMapper) {
  companion object {
    inline fun <reified T> getRequestBody(pattern: RequestPatternBuilder): T = mappingApi.getRequestBody(pattern, jsonMapper = jsonMapper)
  }

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

  fun stubCreatePersonLocationMappingsFailureFollowedBySuccess() {
    mappingApi.stubMappingCreateFailureFollowedBySuccess("/mapping/person-location/migrate", WireMock::put)
  }

  fun stubGetPersonLocationMappings(
    response: List<PersonLocationBookingMappingDto> = listOf(),
    status: HttpStatus = HttpStatus.OK,
    error: ErrorResponse = ErrorResponse(status = status.value()),
  ) {
    mappingApi.stubFor(
      post("/mapping/person-location/nomis-booking-ids/get-list")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .withBody(jsonMapper.writeValueAsString(if (status == HttpStatus.OK) response else error)),
        ),
    )
  }

  fun stubCreatePersonLocationBookingMapping(
    status: HttpStatus = HttpStatus.CREATED,
    error: Any = ErrorResponse(status = status.value()),
  ) {
    mappingApi.stubFor(
      post("/mapping/person-location/booking")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .apply { if (status != HttpStatus.CREATED) withBody(jsonMapper.writeValueAsString(error)) },
        ),
    )
  }

  fun stubCreatePersonLocationBookingMappingFailureFollowedBySuccess() = mappingApi.stubMappingCreateFailureFollowedBySuccess("/mapping/person-location/booking")

  fun stubCreatePersonLocationBookingMappingFailureFollowedByDuplicate(error: Any) {
    mappingApi.stubFor(
      post("/mapping/person-location/booking")
        .inScenario("Retry create booking duplicate Scenario")
        .whenScenarioStateIs(Scenario.STARTED)
        .willReturn(aResponse().withStatus(500).withHeader("Content-Type", "application/json"))
        .willSetStateTo("Cause duplicate"),
    )
    mappingApi.stubFor(
      post("/mapping/person-location/booking")
        .inScenario("Retry create booking duplicate Scenario")
        .whenScenarioStateIs("Cause duplicate")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.CONFLICT.value())
            .withBody(jsonMapper.writeValueAsString(error)),
        )
        .willSetStateTo(Scenario.STARTED),
    )
  }

  fun stubCreatePersonLocationMovementMapping(
    status: HttpStatus = HttpStatus.CREATED,
    error: Any = ErrorResponse(status = status.value()),
  ) {
    mappingApi.stubFor(
      post("/mapping/person-location/movement")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .apply { if (status != HttpStatus.CREATED) withBody(jsonMapper.writeValueAsString(error)) },
        ),
    )
  }

  fun stubCreatePersonLocationMovementMappingFailureFollowedBySuccess() = mappingApi.stubMappingCreateFailureFollowedBySuccess("/mapping/person-location/movement")

  fun stubCreatePersonLocationMovementMappingFailureFollowedByDuplicate(error: Any) {
    mappingApi.stubFor(
      post("/mapping/person-location/movement")
        .inScenario("Retry create duplicate Scenario")
        .whenScenarioStateIs(Scenario.STARTED)
        .willReturn(aResponse().withStatus(500).withHeader("Content-Type", "application/json"))
        .willSetStateTo("Cause duplicate"),
    )
    mappingApi.stubFor(
      post("/mapping/person-location/movement")
        .inScenario("Retry create duplicate Scenario")
        .whenScenarioStateIs("Cause duplicate")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.CONFLICT.value())
            .withBody(jsonMapper.writeValueAsString(error)),
        )
        .willSetStateTo(Scenario.STARTED),
    )
  }

  fun stubGetPersonLocationBookingMapping(
    nomisBookingId: Long = 12345L,
    response: PersonLocationBookingIdMappingDto = personLocationBookingMapping(nomisBookingId = nomisBookingId),
    status: HttpStatus = HttpStatus.OK,
    error: ErrorResponse = ErrorResponse(status = status.value()),
  ) {
    mappingApi.stubFor(
      get("/mapping/person-location/booking/nomis-booking-id/$nomisBookingId")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .withBody(jsonMapper.writeValueAsString(if (status == HttpStatus.OK) response else error)),
        ),
    )
  }

  fun stubGetPersonLocationMovementMapping(
    nomisBookingId: Long = 12345L,
    nomisMovementSeq: Int = 1,
    response: PersonLocationMovementIdMappingDto = personLocationMovementMapping(nomisBookingId = nomisBookingId, nomisMovementSeq = nomisMovementSeq),
    status: HttpStatus = HttpStatus.OK,
    error: ErrorResponse = ErrorResponse(status = status.value()),
  ) {
    mappingApi.stubFor(
      get("/mapping/person-location/movement/nomis-booking-id/$nomisBookingId/nomis-movement-seq/$nomisMovementSeq")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .withBody(jsonMapper.writeValueAsString(if (status == HttpStatus.OK) response else error)),
        ),
    )
  }

  fun stubDeletePersonLocationBookingMapping(
    nomisBookingId: Long = 12345L,
  ) {
    mappingApi.stubFor(
      delete("/mapping/person-location/booking/nomis-booking-id/$nomisBookingId")
        .willReturn(
          aResponse()
            .withStatus(HttpStatus.NO_CONTENT.value()),
        ),
    )
  }

  fun stubDeletePersonLocationMovementMapping(
    nomisBookingId: Long = 12345L,
    nomisMovementSeq: Int = 1,
  ) {
    mappingApi.stubFor(
      delete("/mapping/person-location/movement/nomis-booking-id/$nomisBookingId/nomis-movement-seq/$nomisMovementSeq")
        .willReturn(
          aResponse()
            .withStatus(HttpStatus.NO_CONTENT.value()),
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

fun personLocationBookingMapping(
  nomisBookingId: Long = 12345L,
  dpsCustodialSeriesId: UUID = UUID.randomUUID(),
  mappingType: PersonLocationBookingIdMappingDto.MappingType = PersonLocationBookingIdMappingDto.MappingType.NOMIS_CREATED,
) = PersonLocationBookingIdMappingDto(
  nomisBookingId = nomisBookingId,
  dpsCustodialSeriesId = dpsCustodialSeriesId,
  mappingType = mappingType,
)

fun personLocationMovementMapping(
  nomisBookingId: Long = 12345L,
  nomisMovementSeq: Int = 1,
  dpsExternalMovementId: UUID = UUID.randomUUID(),
  mappingType: PersonLocationMovementIdMappingDto.MappingType = PersonLocationMovementIdMappingDto.MappingType.NOMIS_CREATED,
) = PersonLocationMovementIdMappingDto(
  nomisBookingId = nomisBookingId,
  nomisMovementSeq = nomisMovementSeq,
  dpsExternalMovementId = dpsExternalMovementId,
  mappingType = mappingType,
)
