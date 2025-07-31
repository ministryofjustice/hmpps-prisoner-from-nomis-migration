package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.CountMatchingStrategy
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi

@Component
class ExternalMovementsMappingApiMockServer(private val objectMapper: ObjectMapper) {
  fun stubCreateTemporaryAbsenceMapping() {
    mappingApi.stubFor(
      post("/mapping/temporary-absences")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(200),
        ),
    )
  }

  fun stubCreateTemporaryAbsenceMapping(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      post("/mapping/temporary-absences").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateTemporaryAbsenceMapping(error: DuplicateMappingErrorResponse) {
    mappingApi.stubFor(
      post("/mapping/temporary-absences").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(409)
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateTemporaryAbsenceMappingFailureFollowedBySuccess() {
    mappingApi.stubMappingCreateFailureFollowedBySuccess(url = "/mapping/temporary-absences")
  }

  fun stubGetTemporaryAbsenceMappings(prisonerNumber: String = "A1234BC") {
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/temporary-absences/nomis-prisoner-number/$prisonerNumber")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(objectMapper.writeValueAsString(temporaryAbsencePrisonerMappings(prisonerNumber))),
      ),
    )
  }

  fun stubGetTemporaryAbsenceMappings(prisonerNumber: String = "A1234BC", status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/temporary-absences/nomis-prisoner-number/$prisonerNumber")).willReturn(
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
          dpsMovementApplicationId = 1001,
          outsideMovements = listOf(
            TemporaryAbsencesOutsideMovementMappingDto(
              nomisMovementApplicationMultiId = 1,
              dpsOutsideMovementId = 1001,
            ),
          ),
          scheduledAbsence = ScheduledMovementMappingDto(
            nomisEventId = 1,
            dpsScheduledMovementId = 1001,
          ),
          scheduledAbsenceReturn = ScheduledMovementMappingDto(
            nomisEventId = 2,
            dpsScheduledMovementId = 1002,
          ),
          absence = ExternalMovementMappingDto(
            nomisMovementSeq = 3,
            dpsExternalMovementId = 1003,
          ),
          absenceReturn = ExternalMovementMappingDto(
            nomisMovementSeq = 4,
            dpsExternalMovementId = 1004,
          ),
        ),
      ),
      unscheduledMovements = listOf(
        ExternalMovementMappingDto(
          nomisMovementSeq = 1,
          dpsExternalMovementId = 1001,
        ),
        ExternalMovementMappingDto(
          nomisMovementSeq = 2,
          dpsExternalMovementId = 1002,
        ),
      ),
    ),
  ),
)
