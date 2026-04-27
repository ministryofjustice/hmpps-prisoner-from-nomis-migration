package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.taps

import com.github.tomakehurst.wiremock.client.CountMatchingStrategy
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.delete
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.FindTapScheduleMappingsForAddressResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TapApplicationMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TapApplicationMappingIdsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TapApplicationMappingsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TapBookingMappingsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TapMoveBookingMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TapMovementMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TapMovementMappingIdsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TapMovementMappingsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TapPrisonerMappingIdsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TapPrisonerMappingsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TapScheduleMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TapScheduleMappingIdsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TapScheduleMappingsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.jsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.getRequestBody
import java.time.LocalDateTime
import java.util.*

@Component
class TapMappingApiMockServer(private val jsonMapper: JsonMapper) {
  companion object {
    inline fun <reified T> getRequestBody(pattern: RequestPatternBuilder): T = mappingApi.getRequestBody(pattern, jsonMapper = jsonMapper)
  }

  fun stubCreateTapPrisonerMappings() {
    mappingApi.stubFor(
      put("/mapping/taps/migrate")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(200),
        ),
    )
  }

  fun stubCreateTapPrisonerMappings(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      put("/mapping/taps/migrate").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateTapPrisonerMappingsFailureFollowedBySuccess() {
    mappingApi.stubMappingCreateFailureFollowedBySuccess(url = "/mapping/taps/migrate", WireMock::put)
  }

  fun stubGetTapPrisonerMappingIds(
    prisonerNumber: String = "A1234BC",
    bookingId: Long = 12345,
    nomisApplicationId: Long = 1,
    dpsAuthorisationId: UUID = UUID.randomUUID(),
    nomisScheduleOutEventId: Long = 2,
    dpsOccurrenceId: UUID = UUID.randomUUID(),
    nomisMovementOutSeq: Int = 3,
    dpsMovementOutId: UUID = UUID.randomUUID(),
    nomisMovementInSeq: Int = 4,
    dpsMovementInId: UUID = UUID.randomUUID(),
    nomisUnscheduledMovementOutSeq: Int = 5,
    dpsUnscheduledMovementOutId: UUID = UUID.randomUUID(),
    nomisUnscheduledMovementInSeq: Int = 6,
    dpsUnscheduledMovementInId: UUID = UUID.randomUUID(),
    idMappings: TapPrisonerMappingIdsDto = tapPrisonerIdMappings(bookingId, nomisApplicationId, dpsAuthorisationId, nomisScheduleOutEventId, dpsOccurrenceId, nomisMovementOutSeq, dpsMovementOutId, nomisMovementInSeq, dpsMovementInId, nomisUnscheduledMovementOutSeq, dpsUnscheduledMovementOutId, nomisUnscheduledMovementInSeq, dpsUnscheduledMovementInId),
  ) {
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/taps/$prisonerNumber/ids")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(jsonMapper.writeValueAsString(idMappings)),
      ),
    )
  }

  fun stubGetTapPrisonerMappingIds(prisonerNumber: String = "A1234BC", status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/taps/$prisonerNumber/ids")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateTapApplicationMapping() {
    mappingApi.stubFor(
      post("/mapping/taps/application")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(201),
        ),
    )
  }

  fun stubCreateTapApplicationMapping(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      post("/mapping/taps/application").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateTapApplicationMappingConflict(error: DuplicateMappingErrorResponse) {
    mappingApi.stubFor(
      post("/mapping/taps/application").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(409)
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateTapApplicationMappingFailureFollowedBySuccess() = mappingApi.stubMappingCreateFailureFollowedBySuccess("/mapping/taps/application")

  fun stubGetTapApplicationMapping(nomisApplicationId: Long = 1L, dpsAuthorisationId: UUID = UUID.randomUUID()) {
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/taps/application/nomis-id/$nomisApplicationId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(jsonMapper.writeValueAsString(tapApplicationMapping(nomisApplicationId, dpsAuthorisationId))),
      ),
    )
  }

  fun stubGetTapApplicationMapping(nomisApplicationId: Long = 1L, status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/taps/application/nomis-id/$nomisApplicationId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubDeleteTapApplicationMapping(nomisApplicationId: Long = 1L) {
    mappingApi.stubFor(
      delete(urlPathMatching("/mapping/taps/application/nomis-id/$nomisApplicationId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(204),
      ),
    )
  }

  fun stubDeleteTapApplicationMapping(nomisApplicationId: Long = 1L, status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      delete(urlPathMatching("/mapping/taps/application/nomis-id/$nomisApplicationId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateTapScheduleMapping() {
    mappingApi.stubFor(
      post("/mapping/taps/schedule")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(201),
        ),
    )
  }

  fun stubCreateTapScheduleMapping(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      post("/mapping/taps/schedule").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateTapScheduleMappingConflict(error: DuplicateMappingErrorResponse) {
    mappingApi.stubFor(
      post("/mapping/taps/schedule").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(409)
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateTapScheduleMappingFailureFollowedBySuccess() = mappingApi.stubMappingCreateFailureFollowedBySuccess("/mapping/taps/schedule")

  fun stubUpdateTapScheduleMapping() {
    mappingApi.stubFor(
      put("/mapping/taps/schedule")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(200),
        ),
    )
  }

  fun stubUpdateTapScheduleMapping(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      put("/mapping/taps/schedule").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubUpdateTapScheduleMappingFailureFollowedBySuccess() = mappingApi.stubMappingUpdateFailureFollowedBySuccess("/mapping/taps/schedule")

  fun stubGetTapScheduleMapping(nomisEventId: Long = 1L, dpsOccurrenceId: UUID = UUID.randomUUID(), eventTime: LocalDateTime = LocalDateTime.now(), dpsUprn: Long = 987L) {
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/taps/schedule/nomis-id/$nomisEventId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(jsonMapper.writeValueAsString(tapScheduleMapping(nomisEventId = nomisEventId, dpsOccurrenceId = dpsOccurrenceId, eventTime = eventTime, dpsUprn = dpsUprn))),
      ),
    )
  }

  fun stubGetTapScheduleMapping(nomisEventId: Long = 1L, status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/taps/schedule/nomis-id/$nomisEventId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubDeleteTapScheduleMapping(nomisEventId: Long = 1L) {
    mappingApi.stubFor(
      delete(urlPathMatching("/mapping/taps/schedule/nomis-id/$nomisEventId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(204),
      ),
    )
  }

  fun stubDeleteTapScheduleMapping(nomisEventId: Long = 1L, status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      delete(urlPathMatching("/mapping/taps/schedule/nomis-id/$nomisEventId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateTapMovementMapping() {
    mappingApi.stubFor(
      post("/mapping/taps/movement")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(201),
        ),
    )
  }

  fun stubCreateTapMovementMapping(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      post("/mapping/taps/movement").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateTapMovementMappingConflict(error: DuplicateMappingErrorResponse) {
    mappingApi.stubFor(
      post("/mapping/taps/movement").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(409)
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateTapMovementMappingFailureFollowedBySuccess() = mappingApi.stubMappingCreateFailureFollowedBySuccess("/mapping/taps/movement")

  fun stubUpdateTapMovementMapping() {
    mappingApi.stubFor(
      put("/mapping/taps/movement")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(200),
        ),
    )
  }

  fun stubUpdateTapMovementMapping(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      put("/mapping/taps/movement").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubUpdateTapMovementMappingFailureFollowedBySuccess() = mappingApi.stubMappingUpdateFailureFollowedBySuccess("/mapping/taps/movement")

  fun stubGetTapMovementMapping(
    bookingId: Long = 12345L,
    movementSeq: Int = 1,
    dpsMovementId: UUID = UUID.randomUUID(),
    city: String? = null,
  ) {
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/taps/movement/nomis-id/$bookingId/$movementSeq")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            jsonMapper.writeValueAsString(
              tapMovementMapping(
                bookingId = bookingId,
                movementSeq = movementSeq,
                dpsMovementId = dpsMovementId,
                city = city,
              ),
            ),
          ),
      ),
    )
  }

  fun stubGetTapMovementMapping(bookingId: Long = 12345L, movementSeq: Int = 1, status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/taps/movement/nomis-id/$bookingId/$movementSeq")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubDeleteTapMovementMapping(bookingId: Long = 12345L, movementSeq: Int = 1) {
    mappingApi.stubFor(
      delete(urlPathMatching("/mapping/taps/movement/nomis-id/$bookingId/$movementSeq")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(204),
      ),
    )
  }

  fun stubDeleteTapMovementMapping(bookingId: Long = 12345L, movementSeq: Int = 1, status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      delete(urlPathMatching("/mapping/taps/movement/nomis-id/$bookingId/$movementSeq")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubFindTapScheduleMappingsForAddressForPrisoners(
    nomisAddressId: Long = 123L,
    prisoners: List<String> = listOf("A1234AA", "B1234BB"),
  ) {
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/taps/schedule/nomis-address-id/$nomisAddressId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            jsonMapper.writeValueAsString(
              FindTapScheduleMappingsForAddressResponse(
                prisoners.mapIndexed { index, _ ->
                  tapScheduleMapping(prisonerNumber = prisoners[index])
                },
              ),
            ),
          ),
      ),
    )
  }

  fun stubFindTapScheduleMappingsForAddressForMappings(
    nomisAddressId: Long = 321L,
    mappings: List<TapScheduleMappingDto>,
  ) {
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/taps/schedule/nomis-address-id/$nomisAddressId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            jsonMapper.writeValueAsString(
              FindTapScheduleMappingsForAddressResponse(scheduleMappings = mappings),
            ),
          ),
      ),
    )
  }

  fun stubFindTapScheduleMappingsForAddressError(nomisAddressId: Long = 123L, status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/taps/schedule/nomis-address-id/$nomisAddressId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubGetMoveBookingMappings(
    bookingId: Long = 12345,
    mappings: TapMoveBookingMappingDto,
  ) {
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/taps/move-booking/$bookingId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(jsonMapper.writeValueAsString(mappings)),
      ),
    )
  }

  fun stubGetMoveBookingMappingsError(bookingId: Long = 12345, status: HttpStatus = HttpStatus.INTERNAL_SERVER_ERROR, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/taps/move-booking/$bookingId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubMoveBookingMappings(bookingId: Long = 12345L, fromOffenderNo: String = "A1234AA", toOffenderNo: String = "B1234BB") {
    mappingApi.stubFor(
      put("/mapping/taps/move-booking/$bookingId/from/$fromOffenderNo/to/$toOffenderNo")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(200),
        ),
    )
  }

  fun stubMoveBookingMappingsError(bookingId: Long = 12345L, fromOffenderNo: String = "A1234AA", toOffenderNo: String = "B1234BB", status: HttpStatus = HttpStatus.INTERNAL_SERVER_ERROR, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      put("/mapping/taps/move-booking/$bookingId/from/$fromOffenderNo/to/$toOffenderNo").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubMoveBookingMappingsFailureFollowedBySuccess(bookingId: Long = 12345L, fromOffenderNo: String = "A1234AA", toOffenderNo: String = "B1234BB") = mappingApi.stubMappingUpdateFailureFollowedBySuccess("/mapping/taps/move-booking/$bookingId/from/$fromOffenderNo/to/$toOffenderNo")

  fun verify(pattern: RequestPatternBuilder) = mappingApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = mappingApi.verify(count, pattern)
  fun verify(count: CountMatchingStrategy, pattern: RequestPatternBuilder) = mappingApi.verify(count, pattern)
}

fun tapPrisonerMappings(prisonerNumber: String = "A1234BC") = TapPrisonerMappingsDto(
  prisonerNumber = prisonerNumber,
  migrationId = "2020-01-01T11:10:00",
  bookings = listOf(
    TapBookingMappingsDto(
      bookingId = 12345,
      applications = listOf(
        TapApplicationMappingsDto(
          nomisApplicationId = 1,
          dpsAuthorisationId = UUID.randomUUID(),
          schedules = listOf(
            TapScheduleMappingsDto(
              nomisEventId = 1,
              dpsOccurrenceId = UUID.randomUUID(),
              nomisAddressId = 321,
              nomisAddressOwnerClass = "OFF",
              dpsAddressText = "schedule OFF address",
              eventTime = "${LocalDateTime.now()}",
            ),
            TapScheduleMappingsDto(
              nomisEventId = 2,
              dpsOccurrenceId = UUID.randomUUID(),
              nomisAddressId = 432,
              nomisAddressOwnerClass = "CORP",
              dpsAddressText = "schedule CORP address",
              eventTime = "${LocalDateTime.now()}",
            ),
          ),
          movements = listOf(
            TapMovementMappingsDto(
              nomisMovementSeq = 3,
              dpsMovementId = UUID.randomUUID(),
              nomisAddressId = 543,
              nomisAddressOwnerClass = "AGY",
              dpsAddressText = "movement AGY address",
            ),
            TapMovementMappingsDto(
              nomisMovementSeq = 4,
              dpsMovementId = UUID.randomUUID(),
              nomisAddressId = 654,
              nomisAddressOwnerClass = "OFF",
              dpsAddressText = "movement OFF address",
            ),
          ),
        ),
      ),
      unscheduledMovements = listOf(
        TapMovementMappingsDto(
          nomisMovementSeq = 1,
          dpsMovementId = UUID.randomUUID(),
          nomisAddressId = 654,
          nomisAddressOwnerClass = "CORP",
          dpsAddressText = "movement CORP address",
        ),
        TapMovementMappingsDto(
          nomisMovementSeq = 2,
          dpsMovementId = UUID.randomUUID(),
          nomisAddressId = 765,
          nomisAddressOwnerClass = "OFF",
          dpsAddressText = "movement OFF address",
        ),
      ),
    ),
  ),
)

fun tapApplicationMapping(
  nomisApplicationId: Long = 1L,
  dpsAuthorisationId: UUID = UUID.randomUUID(),
  prisonerNumber: String = "A1234BC",
) = TapApplicationMappingDto(
  prisonerNumber = prisonerNumber,
  bookingId = 12345,
  nomisApplicationId = nomisApplicationId,
  dpsAuthorisationId = dpsAuthorisationId,
  mappingType = TapApplicationMappingDto.MappingType.MIGRATED,
)

fun tapScheduleMapping(
  nomisEventId: Long = 1L,
  prisonerNumber: String = "A1234BC",
  dpsOccurrenceId: UUID = UUID.randomUUID(),
  eventTime: LocalDateTime = LocalDateTime.now(),
  nomisAddressOwnerClass: String = "OFF",
  dpsUprn: Long? = null,
) = TapScheduleMappingDto(
  prisonerNumber = prisonerNumber,
  bookingId = 12345,
  nomisEventId = nomisEventId,
  dpsOccurrenceId = dpsOccurrenceId,
  mappingType = TapScheduleMappingDto.MappingType.MIGRATED,
  nomisAddressId = 321,
  nomisAddressOwnerClass = nomisAddressOwnerClass,
  dpsAddressText = "to full address",
  dpsDescription = "some description",
  dpsPostcode = "S1 1AB",
  dpsUprn = dpsUprn,
  eventTime = "$eventTime",
)

fun tapMovementMapping(
  bookingId: Long = 12345L,
  movementSeq: Int = 1,
  prisonerNumber: String = "A1234BC",
  dpsMovementId: UUID = UUID.randomUUID(),
  city: String? = null,
) = TapMovementMappingDto(
  prisonerNumber = prisonerNumber,
  bookingId = bookingId,
  nomisMovementSeq = movementSeq,
  dpsMovementId = dpsMovementId,
  mappingType = TapMovementMappingDto.MappingType.MIGRATED,
  nomisAddressId = if (city == null) 321 else null,
  nomisAddressOwnerClass = if (city == null) "OFF" else null,
  dpsAddressText = city ?: "full address",
  dpsDescription = if (city == null) "some description" else null,
  dpsPostcode = if (city == null) "S1 1AB" else null,
)

fun tapPrisonerIdMappings(
  bookingId: Long = 12345,
  nomisApplicationId: Long = 1,
  dpsAuthorisationId: UUID = UUID.randomUUID(),
  nomisScheduleOutEventId: Long = 2,
  dpsOccurrenceId: UUID = UUID.randomUUID(),
  nomisMovementOutSeq: Int = 3,
  dpsMovementOutId: UUID = UUID.randomUUID(),
  nomisMovementInSeq: Int = 4,
  dpsMovementInId: UUID = UUID.randomUUID(),
  nomisUnscheduledMovementOutSeq: Int = 5,
  dpsUnscheduledMovementOutId: UUID = UUID.randomUUID(),
  nomisUnscheduledMovementInSeq: Int = 6,
  dpsUnscheduledMovementInId: UUID = UUID.randomUUID(),
) = TapPrisonerMappingIdsDto(
  prisonerNumber = "A1234BC",
  applications = listOf(TapApplicationMappingIdsDto(nomisApplicationId, dpsAuthorisationId)),
  schedules = listOf(TapScheduleMappingIdsDto(nomisScheduleOutEventId, dpsOccurrenceId)),
  movements = listOf(
    TapMovementMappingIdsDto(bookingId, nomisMovementOutSeq, dpsMovementOutId),
    TapMovementMappingIdsDto(bookingId, nomisMovementInSeq, dpsMovementInId),
    TapMovementMappingIdsDto(bookingId, nomisUnscheduledMovementOutSeq, dpsUnscheduledMovementOutId),
    TapMovementMappingIdsDto(bookingId, nomisUnscheduledMovementInSeq, dpsUnscheduledMovementInId),
  ),
)
