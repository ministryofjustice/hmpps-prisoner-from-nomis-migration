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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ScheduledMovementMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TemporaryAbsenceApplicationMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TemporaryAbsenceApplicationSyncMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TemporaryAbsenceBookingMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TemporaryAbsencesOutsideMovementMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TemporaryAbsencesPrisonerMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import java.util.UUID

@Component
class ExternalMovementsMappingApiMockServer(private val objectMapper: ObjectMapper) {
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
            .withStatus(200),
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

  fun stubGetTemporaryAbsenceApplicationMapping(nomisApplicationId: Long = 1L) {
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/temporary-absence/application/nomis-application-id/$nomisApplicationId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(objectMapper.writeValueAsString(temporaryAbsenceApplicationMapping(nomisApplicationId))),
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
          .withStatus(200),
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
              dpsScheduledMovementId = UUID.randomUUID(),
            ),
            ScheduledMovementMappingDto(
              nomisEventId = 2,
              dpsScheduledMovementId = UUID.randomUUID(),
            ),
          ),
          movements = listOf(
            ExternalMovementMappingDto(
              nomisMovementSeq = 3,
              dpsExternalMovementId = UUID.randomUUID(),
            ),
            ExternalMovementMappingDto(
              nomisMovementSeq = 4,
              dpsExternalMovementId = UUID.randomUUID(),
            ),
          ),
        ),
      ),
      unscheduledMovements = listOf(
        ExternalMovementMappingDto(
          nomisMovementSeq = 1,
          dpsExternalMovementId = UUID.randomUUID(),
        ),
        ExternalMovementMappingDto(
          nomisMovementSeq = 2,
          dpsExternalMovementId = UUID.randomUUID(),
        ),
      ),
    ),
  ),
)

fun temporaryAbsenceApplicationMapping(nomisApplicationId: Long = 1L, prisonerNumber: String = "A1234BC") = TemporaryAbsenceApplicationSyncMappingDto(
  prisonerNumber = prisonerNumber,
  bookingId = 12345,
  nomisMovementApplicationId = nomisApplicationId,
  dpsMovementApplicationId = UUID.randomUUID(),
  mappingType = TemporaryAbsenceApplicationSyncMappingDto.MappingType.MIGRATED,
)
