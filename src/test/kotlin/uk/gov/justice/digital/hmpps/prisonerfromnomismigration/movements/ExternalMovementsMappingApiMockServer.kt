package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements

import com.fasterxml.jackson.databind.ObjectMapper
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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ExternalMovementMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ExternalMovementSyncMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.FindScheduledMovementsForAddressResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ScheduledMovementMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ScheduledMovementSyncMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TemporaryAbsenceApplicationMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TemporaryAbsenceApplicationSyncMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TemporaryAbsenceBookingMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TemporaryAbsenceOutsideMovementSyncMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TemporaryAbsencesOutsideMovementMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TemporaryAbsencesPrisonerMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.objectMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.getRequestBody
import java.time.LocalDateTime
import java.util.UUID

@Component
class ExternalMovementsMappingApiMockServer(private val objectMapper: ObjectMapper) {
  companion object {
    inline fun <reified T> getRequestBody(pattern: RequestPatternBuilder): T = mappingApi.getRequestBody(pattern, objectMapper = objectMapper)
  }

  fun stubCreateTemporaryAbsenceMapping() {
    mappingApi.stubFor(
      put("/mapping/temporary-absence/migrate")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(200),
        ),
    )
  }

  fun stubCreateTemporaryAbsenceMapping(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      put("/mapping/temporary-absence/migrate").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateTemporaryAbsenceMappingFailureFollowedBySuccess() {
    mappingApi.stubMappingCreateFailureFollowedBySuccess(url = "/mapping/temporary-absence/migrate", WireMock::put)
  }

  fun stubGetTemporaryAbsenceMappings(prisonerNumber: String = "A1234BC") {
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/temporary-absence/nomis-prisoner-number/$prisonerNumber")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(objectMapper.writeValueAsString(temporaryAbsencePrisonerMappings(prisonerNumber))),
      ),
    )
  }

  fun stubGetTemporaryAbsenceMappings(prisonerNumber: String = "A1234BC", status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/temporary-absence/nomis-prisoner-number/$prisonerNumber")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateTemporaryAbsenceApplicationMapping() {
    mappingApi.stubFor(
      post("/mapping/temporary-absence/application")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(201),
        ),
    )
  }

  fun stubCreateTemporaryAbsenceApplicationMapping(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      post("/mapping/temporary-absence/application").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateTemporaryAbsenceApplicationMappingConflict(error: DuplicateMappingErrorResponse) {
    mappingApi.stubFor(
      post("/mapping/temporary-absence/application").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(409)
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateTemporaryAbsenceApplicationMappingFailureFollowedBySuccess() = mappingApi.stubMappingCreateFailureFollowedBySuccess("/mapping/temporary-absence/application")

  fun stubGetTemporaryAbsenceApplicationMapping(nomisApplicationId: Long = 1L, dpsApplicationId: UUID = UUID.randomUUID()) {
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/temporary-absence/application/nomis-application-id/$nomisApplicationId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(objectMapper.writeValueAsString(temporaryAbsenceApplicationMapping(nomisApplicationId, dpsApplicationId))),
      ),
    )
  }

  fun stubGetTemporaryAbsenceApplicationMapping(nomisApplicationId: Long = 1L, status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/temporary-absence/application/nomis-application-id/$nomisApplicationId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubDeleteTemporaryAbsenceApplicationMapping(nomisApplicationId: Long = 1L) {
    mappingApi.stubFor(
      delete(urlPathMatching("/mapping/temporary-absence/application/nomis-application-id/$nomisApplicationId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(204),
      ),
    )
  }

  fun stubDeleteTemporaryAbsenceApplicationMapping(nomisApplicationId: Long = 1L, status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      delete(urlPathMatching("/mapping/temporary-absence/application/nomis-application-id/$nomisApplicationId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateOutsideMovementMapping() {
    mappingApi.stubFor(
      post("/mapping/temporary-absence/outside-movement")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(201),
        ),
    )
  }

  fun stubCreateOutsideMovementMapping(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      post("/mapping/temporary-absence/outside-movement").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateOutsideMovementMappingConflict(error: DuplicateMappingErrorResponse) {
    mappingApi.stubFor(
      post("/mapping/temporary-absence/outside-movement").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(409)
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateOutsideMovementMappingFailureFollowedBySuccess() = mappingApi.stubMappingCreateFailureFollowedBySuccess("/mapping/temporary-absence/outside-movement")

  fun stubGetOutsideMovementMapping(nomisApplicationMultiId: Long = 1L) {
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/temporary-absence/outside-movement/nomis-application-multi-id/$nomisApplicationMultiId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(objectMapper.writeValueAsString(temporaryAbsenceOutsideMovementMapping(nomisApplicationMultiId))),
      ),
    )
  }

  fun stubGetOutsideMovementMapping(nomisApplicationMultiId: Long = 1L, status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/temporary-absence/outside-movement/nomis-application-multi-id/$nomisApplicationMultiId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubDeleteOutsideMovementMapping(nomisApplicationMultiId: Long = 1L) {
    mappingApi.stubFor(
      delete(urlPathMatching("/mapping/temporary-absence/outside-movement/nomis-application-multi-id/$nomisApplicationMultiId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(204),
      ),
    )
  }

  fun stubDeleteOutsideMovementMapping(nomisApplicationMultiId: Long = 1L, status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      delete(urlPathMatching("/mapping/temporary-absence/outside-movement/nomis-application-multi-id/$nomisApplicationMultiId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateScheduledMovementMapping() {
    mappingApi.stubFor(
      post("/mapping/temporary-absence/scheduled-movement")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(201),
        ),
    )
  }

  fun stubCreateScheduledMovementMapping(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      post("/mapping/temporary-absence/scheduled-movement").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateScheduledMovementMappingConflict(error: DuplicateMappingErrorResponse) {
    mappingApi.stubFor(
      post("/mapping/temporary-absence/scheduled-movement").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(409)
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateScheduledMovementMappingFailureFollowedBySuccess() = mappingApi.stubMappingCreateFailureFollowedBySuccess("/mapping/temporary-absence/scheduled-movement")

  fun stubUpdateScheduledMovementMapping() {
    mappingApi.stubFor(
      put("/mapping/temporary-absence/scheduled-movement")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(200),
        ),
    )
  }

  fun stubUpdateScheduledMovementMapping(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      put("/mapping/temporary-absence/scheduled-movement").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubUpdateScheduledMovementMappingFailureFollowedBySuccess() = mappingApi.stubMappingUpdateFailureFollowedBySuccess("/mapping/temporary-absence/scheduled-movement")

  fun stubGetScheduledMovementMapping(nomisEventId: Long = 1L, dpsOccurrenceId: UUID = UUID.randomUUID(), eventTime: LocalDateTime = LocalDateTime.now()) {
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/temporary-absence/scheduled-movement/nomis-event-id/$nomisEventId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(objectMapper.writeValueAsString(temporaryAbsenceScheduledMovementMapping(nomisEventId = nomisEventId, dpsOccurrenceId = dpsOccurrenceId, eventTime = eventTime))),
      ),
    )
  }

  fun stubGetScheduledMovementMapping(nomisEventId: Long = 1L, status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/temporary-absence/scheduled-movement/nomis-event-id/$nomisEventId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubDeleteScheduledMovementMapping(nomisEventId: Long = 1L) {
    mappingApi.stubFor(
      delete(urlPathMatching("/mapping/temporary-absence/scheduled-movement/nomis-event-id/$nomisEventId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(204),
      ),
    )
  }

  fun stubDeleteScheduledMovementMapping(nomisEventId: Long = 1L, status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      delete(urlPathMatching("/mapping/temporary-absence/scheduled-movement/nomis-event-id/$nomisEventId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateExternalMovementMapping() {
    mappingApi.stubFor(
      post("/mapping/temporary-absence/external-movement")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(201),
        ),
    )
  }

  fun stubCreateExternalMovementMapping(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      post("/mapping/temporary-absence/external-movement").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateExternalMovementMappingConflict(error: DuplicateMappingErrorResponse) {
    mappingApi.stubFor(
      post("/mapping/temporary-absence/external-movement").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(409)
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateExternalMovementMappingFailureFollowedBySuccess() = mappingApi.stubMappingCreateFailureFollowedBySuccess("/mapping/temporary-absence/external-movement")

  fun stubUpdateExternalMovementMapping() {
    mappingApi.stubFor(
      put("/mapping/temporary-absence/external-movement")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(200),
        ),
    )
  }

  fun stubUpdateExternalMovementMapping(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      put("/mapping/temporary-absence/external-movement").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubUpdateExternalMovementMappingFailureFollowedBySuccess() = mappingApi.stubMappingUpdateFailureFollowedBySuccess("/mapping/temporary-absence/external-movement")

  fun stubGetExternalMovementMapping(
    bookingId: Long = 12345L,
    movementSeq: Int = 1,
    dpsMovementId: UUID = UUID.randomUUID(),
  ) {
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/temporary-absence/external-movement/nomis-movement-id/$bookingId/$movementSeq")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            objectMapper.writeValueAsString(
              temporaryAbsenceExternalMovementMapping(
                bookingId,
                movementSeq,
                dpsMovementId = dpsMovementId,
              ),
            ),
          ),
      ),
    )
  }

  fun stubGetExternalMovementMapping(bookingId: Long = 12345L, movementSeq: Int = 1, status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/temporary-absence/external-movement/nomis-movement-id/$bookingId/$movementSeq")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubDeleteExternalMovementMapping(bookingId: Long = 12345L, movementSeq: Int = 1) {
    mappingApi.stubFor(
      delete(urlPathMatching("/mapping/temporary-absence/external-movement/nomis-movement-id/$bookingId/$movementSeq")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(204),
      ),
    )
  }

  fun stubDeleteExternalMovementMapping(bookingId: Long = 12345L, movementSeq: Int = 1, status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      delete(urlPathMatching("/mapping/temporary-absence/external-movement/nomis-movement-id/$bookingId/$movementSeq")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubFindScheduledMovementsForAddress(
    nomisAddressId: Long = 123L,
    prisoners: List<String> = listOf("A1234AA", "B1234BB"),
  ) {
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/temporary-absence/scheduled-movements/nomis-address-id/$nomisAddressId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            objectMapper.writeValueAsString(
              FindScheduledMovementsForAddressResponse(
                prisoners.mapIndexed { index, _ ->
                  temporaryAbsenceScheduledMovementMapping(prisonerNumber = prisoners[index])
                },
              ),
            ),
          ),
      ),
    )
  }

  fun stubFindScheduledMovementsForAddressMappings(
    nomisAddressId: Long = 321L,
    mappings: List<ScheduledMovementSyncMappingDto>,
  ) {
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/temporary-absence/scheduled-movements/nomis-address-id/$nomisAddressId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            objectMapper.writeValueAsString(
              FindScheduledMovementsForAddressResponse(scheduleMappings = mappings),
            ),
          ),
      ),
    )
  }

  fun stubFindScheduledMovementsForAddressError(nomisAddressId: Long = 123L, status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/temporary-absence/scheduled-movements/nomis-address-id/$nomisAddressId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = mappingApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = mappingApi.verify(count, pattern)
  fun verify(count: CountMatchingStrategy, pattern: RequestPatternBuilder) = mappingApi.verify(count, pattern)
}

fun temporaryAbsencePrisonerMappings(prisonerNumber: String = "A1234BC") = TemporaryAbsencesPrisonerMappingDto(
  prisonerNumber = prisonerNumber,
  migrationId = "2020-01-01T11:10:00",
  bookings = listOf(
    TemporaryAbsenceBookingMappingDto(
      bookingId = 12345,
      applications = listOf(
        TemporaryAbsenceApplicationMappingDto(
          nomisMovementApplicationId = 1,
          dpsMovementApplicationId = UUID.randomUUID(),
          outsideMovements = listOf(
            TemporaryAbsencesOutsideMovementMappingDto(
              nomisMovementApplicationMultiId = 1,
              dpsOutsideMovementId = UUID.randomUUID(),
            ),
          ),
          schedules = listOf(
            ScheduledMovementMappingDto(
              nomisEventId = 1,
              dpsOccurrenceId = UUID.randomUUID(),
              nomisAddressId = 321,
              nomisAddressOwnerClass = "OFF",
              dpsAddressText = "schedule OFF address",
              eventTime = "${LocalDateTime.now()}",
            ),
            ScheduledMovementMappingDto(
              nomisEventId = 2,
              dpsOccurrenceId = UUID.randomUUID(),
              nomisAddressId = 432,
              nomisAddressOwnerClass = "CORP",
              dpsAddressText = "schedule CORP address",
              eventTime = "${LocalDateTime.now()}",
            ),
          ),
          movements = listOf(
            ExternalMovementMappingDto(
              nomisMovementSeq = 3,
              dpsMovementId = UUID.randomUUID(),
              nomisAddressId = 543,
              nomisAddressOwnerClass = "AGY",
              dpsAddressText = "movement AGY address",
            ),
            ExternalMovementMappingDto(
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
        ExternalMovementMappingDto(
          nomisMovementSeq = 1,
          dpsMovementId = UUID.randomUUID(),
          nomisAddressId = 654,
          nomisAddressOwnerClass = "CORP",
          dpsAddressText = "movement CORP address",
        ),
        ExternalMovementMappingDto(
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

fun temporaryAbsenceApplicationMapping(
  nomisApplicationId: Long = 1L,
  dpsApplicationId: UUID = UUID.randomUUID(),
  prisonerNumber: String = "A1234BC",
) = TemporaryAbsenceApplicationSyncMappingDto(
  prisonerNumber = prisonerNumber,
  bookingId = 12345,
  nomisMovementApplicationId = nomisApplicationId,
  dpsMovementApplicationId = dpsApplicationId,
  mappingType = TemporaryAbsenceApplicationSyncMappingDto.MappingType.MIGRATED,
)

fun temporaryAbsenceOutsideMovementMapping(nomisApplicationMultiId: Long = 1L, prisonerNumber: String = "A1234BC") = TemporaryAbsenceOutsideMovementSyncMappingDto(
  prisonerNumber = prisonerNumber,
  bookingId = 12345,
  nomisMovementApplicationMultiId = nomisApplicationMultiId,
  dpsOutsideMovementId = UUID.randomUUID(),
  mappingType = TemporaryAbsenceOutsideMovementSyncMappingDto.MappingType.MIGRATED,
)

fun temporaryAbsenceScheduledMovementMapping(
  nomisEventId: Long = 1L,
  prisonerNumber: String = "A1234BC",
  dpsOccurrenceId: UUID = UUID.randomUUID(),
  eventTime: LocalDateTime = LocalDateTime.now(),
  nomisAddressOwnerClass: String = "OFF",
) = ScheduledMovementSyncMappingDto(
  prisonerNumber = prisonerNumber,
  bookingId = 12345,
  nomisEventId = nomisEventId,
  dpsOccurrenceId = dpsOccurrenceId,
  mappingType = ScheduledMovementSyncMappingDto.MappingType.MIGRATED,
  nomisAddressId = 321,
  nomisAddressOwnerClass = nomisAddressOwnerClass,
  dpsAddressText = "to full address",
  eventTime = "$eventTime",
)

fun temporaryAbsenceExternalMovementMapping(
  bookingId: Long = 12345L,
  movementSeq: Int = 1,
  prisonerNumber: String = "A1234BC",
  dpsMovementId: UUID = UUID.randomUUID(),
) = ExternalMovementSyncMappingDto(
  prisonerNumber = prisonerNumber,
  bookingId = bookingId,
  nomisMovementSeq = movementSeq,
  dpsMovementId = dpsMovementId,
  mappingType = ExternalMovementSyncMappingDto.MappingType.MIGRATED,
  nomisAddressId = 321,
  nomisAddressOwnerClass = "OFF",
  dpsAddressText = "full address",
)
