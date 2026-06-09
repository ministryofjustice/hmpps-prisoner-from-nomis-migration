package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.court

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
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.BookingCourtMovementMappingsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.BookingCourtScheduleMappingsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtMovementMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtMovementMappingIdsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtScheduleMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtScheduleMappingIdsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtSchedulerBookingMappingsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtSchedulerMoveBookingMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtSchedulerPrisonerMappingIdsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtSchedulerPrisonerMappingsDto
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
      post("/mapping/court-scheduler/schedule")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(201),
        ),
    )
  }

  fun stubCreateCourtScheduleMapping(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      post("/mapping/court-scheduler/schedule").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateCourtScheduleMappingConflict(error: DuplicateMappingErrorResponse) {
    mappingApi.stubFor(
      post("/mapping/court-scheduler/schedule").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(409)
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateCourtScheduleMappingFailureFollowedBySuccess() = mappingApi.stubMappingCreateFailureFollowedBySuccess("/mapping/court-scheduler/schedule")

  fun stubGetCourtScheduleMapping(nomisEventId: Long = 1L, dpsCourtAppearanceId: UUID = UUID.randomUUID()) {
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/court-scheduler/schedule/nomis-id/$nomisEventId")).willReturn(
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
      get(urlPathMatching("/mapping/court-scheduler/schedule/nomis-id/$nomisEventId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubDeleteCourtScheduleMapping(nomisEventId: Long = 1L) {
    mappingApi.stubFor(
      delete(urlPathMatching("/mapping/court-scheduler/schedule/nomis-id/$nomisEventId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(204),
      ),
    )
  }

  fun stubDeleteCourtScheduleMapping(nomisEventId: Long = 1L, status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      delete(urlPathMatching("/mapping/court-scheduler/schedule/nomis-id/$nomisEventId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateCourtMovementMapping() {
    mappingApi.stubFor(
      post("/mapping/court-scheduler/movement")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(201),
        ),
    )
  }

  fun stubCreateCourtMovementMapping(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      post("/mapping/court-scheduler/movement").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateCourtMovementMappingConflict(error: DuplicateMappingErrorResponse) {
    mappingApi.stubFor(
      post("/mapping/court-scheduler/movement").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(409)
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateCourtMovementMappingFailureFollowedBySuccess() = mappingApi.stubMappingCreateFailureFollowedBySuccess("/mapping/court-scheduler/movement")

  fun stubGetCourtMovementMapping(nomisBookingId: Long = 12345L, nomisMovementSeq: Int = 3, dpsCourtMovementId: UUID = UUID.randomUUID()) {
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/court-scheduler/movement/nomis-id/$nomisBookingId/$nomisMovementSeq")).willReturn(
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
      get(urlPathMatching("/mapping/court-scheduler/movement/nomis-id/$nomisBookingId/$nomisMovementSeq")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubDeleteCourtMovementMapping(bookingId: Long = 12345L, movementSeq: Int = 1) {
    mappingApi.stubFor(
      delete(urlPathMatching("/mapping/court-scheduler/movement/nomis-id/$bookingId/$movementSeq")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(204),
      ),
    )
  }

  fun stubDeleteCourtMovementMapping(bookingId: Long = 12345L, movementSeq: Int = 1, status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      delete(urlPathMatching("/mapping/court-scheduler/movement/nomis-id/$bookingId/$movementSeq")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateCourtSchedulerPrisonerMappings() {
    mappingApi.stubFor(
      put("/mapping/court-scheduler/migrate")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(200),
        ),
    )
  }

  fun stubCreateCourtSchedulerPrisonerMappings(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      put("/mapping/court-scheduler/migrate").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateCourtSchedulePrisonerMappingsFailureFollowedBySuccess() {
    mappingApi.stubMappingCreateFailureFollowedBySuccess("/mapping/court-scheduler/migrate", WireMock::put)
  }

  fun stubGetCourtSchedulerPrisonerMappingIds(
    prisonerNumber: String = "A1234BC",
    bookingId: Long = 12345,
    nomisEventId: Long = 1,
    dpsCourtAppearanceId: UUID = UUID.randomUUID(),
    nomisMovementOutSeq: Int = 3,
    dpsMovementOutId: UUID = UUID.randomUUID(),
    nomisMovementInSeq: Int = 4,
    dpsMovementInId: UUID = UUID.randomUUID(),
    nomisUnscheduledMovementOutSeq: Int = 1,
    dpsUnscheduledMovementOutId: UUID = UUID.randomUUID(),
    nomisUnscheduledMovementInSeq: Int = 2,
    dpsUnscheduledMovementInId: UUID = UUID.randomUUID(),
    idMappings: CourtSchedulerPrisonerMappingIdsDto = courtSchedulerPrisonerIdMappings(bookingId, nomisEventId, dpsCourtAppearanceId, nomisMovementOutSeq, dpsMovementOutId, nomisMovementInSeq, dpsMovementInId, nomisUnscheduledMovementOutSeq, dpsUnscheduledMovementOutId, nomisUnscheduledMovementInSeq, dpsUnscheduledMovementInId),
  ) {
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/court-scheduler/$prisonerNumber/ids")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(jsonMapper.writeValueAsString(idMappings)),
      ),
    )
  }

  fun stubGetCourtSchedulerPrisonerMappingIds(prisonerNumber: String = "A1234BC", status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/court-scheduler/$prisonerNumber/ids")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubGetMoveBookingMappings(
    bookingId: Long = 12345,
    mappings: CourtSchedulerMoveBookingMappingDto,
  ) {
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/court-scheduler/move-booking/$bookingId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(jsonMapper.writeValueAsString(mappings)),
      ),
    )
  }

  fun stubGetMoveBookingMappingsError(
    bookingId: Long = 12345,
    status: HttpStatus = INTERNAL_SERVER_ERROR,
    error: ErrorResponse = ErrorResponse(status = status.value()),
  ) {
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/court-scheduler/move-booking/$bookingId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubMoveBookingMappings(bookingId: Long = 12345L, fromOffenderNo: String = "A1234AA", toOffenderNo: String = "B1234BB") {
    mappingApi.stubFor(
      put("/mapping/court-scheduler/move-booking/$bookingId/from/$fromOffenderNo/to/$toOffenderNo")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(200),
        ),
    )
  }

  fun stubMoveBookingMappingsError(
    bookingId: Long = 12345L,
    fromOffenderNo: String = "A1234AA",
    toOffenderNo: String = "B1234BB",
    status: HttpStatus = INTERNAL_SERVER_ERROR,
    error: ErrorResponse = ErrorResponse(status = status.value()),
  ) {
    mappingApi.stubFor(
      put("/mapping/court-scheduler/move-booking/$bookingId/from/$fromOffenderNo/to/$toOffenderNo").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubMoveBookingMappingsFailureFollowedBySuccess(bookingId: Long = 12345L, fromOffenderNo: String = "A1234AA", toOffenderNo: String = "B1234BB") = mappingApi.stubMappingUpdateFailureFollowedBySuccess("/mapping/court-scheduler/move-booking/$bookingId/from/$fromOffenderNo/to/$toOffenderNo")

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

fun courtSchedulerPrisonerMappings(prisonerNumber: String = "A1234BC") = CourtSchedulerPrisonerMappingsDto(
  offenderNo = prisonerNumber,
  migrationId = "2020-01-01T11:10:00",
  bookings = listOf(
    CourtSchedulerBookingMappingsDto(
      bookingId = 12345,
      courtSchedules = listOf(
        BookingCourtScheduleMappingsDto(
          nomisEventId = 1,
          dpsCourtAppearanceId = UUID.randomUUID(),
          movements = listOf(
            BookingCourtMovementMappingsDto(
              nomisMovementSeq = 3,
              dpsCourtMovementId = UUID.randomUUID(),
            ),
            BookingCourtMovementMappingsDto(
              nomisMovementSeq = 4,
              dpsCourtMovementId = UUID.randomUUID(),
            ),
          ),
        ),
      ),
      unscheduledMovements = listOf(
        BookingCourtMovementMappingsDto(
          nomisMovementSeq = 1,
          dpsCourtMovementId = UUID.randomUUID(),
        ),
        BookingCourtMovementMappingsDto(
          nomisMovementSeq = 2,
          dpsCourtMovementId = UUID.randomUUID(),
        ),
      ),
    ),
  ),
)

fun courtSchedulerPrisonerIdMappings(
  bookingId: Long = 12345,
  nomisEventId: Long = 1,
  dpsCourtAppearanceId: UUID = UUID.randomUUID(),
  nomisMovementOutSeq: Int = 3,
  dpsMovementOutId: UUID = UUID.randomUUID(),
  nomisMovementInSeq: Int = 4,
  dpsMovementInId: UUID = UUID.randomUUID(),
  nomisUnscheduledMovementOutSeq: Int = 1,
  dpsUnscheduledMovementOutId: UUID = UUID.randomUUID(),
  nomisUnscheduledMovementInSeq: Int = 2,
  dpsUnscheduledMovementInId: UUID = UUID.randomUUID(),
) = CourtSchedulerPrisonerMappingIdsDto(
  prisonerNumber = "A1234BC",
  schedules = listOf(CourtScheduleMappingIdsDto(nomisEventId, dpsCourtAppearanceId)),
  movements = listOf(
    CourtMovementMappingIdsDto(bookingId, nomisMovementOutSeq, dpsMovementOutId),
    CourtMovementMappingIdsDto(bookingId, nomisMovementInSeq, dpsMovementInId),
    CourtMovementMappingIdsDto(bookingId, nomisUnscheduledMovementOutSeq, dpsUnscheduledMovementOutId),
    CourtMovementMappingIdsDto(bookingId, nomisUnscheduledMovementInSeq, dpsUnscheduledMovementInId),
  ),
)
