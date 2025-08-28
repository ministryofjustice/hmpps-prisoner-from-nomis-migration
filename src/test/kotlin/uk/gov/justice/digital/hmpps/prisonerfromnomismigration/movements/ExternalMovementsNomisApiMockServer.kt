package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.Absence
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.BookingTemporaryAbsences
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.NomisAudit
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderTemporaryAbsencesResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.ScheduledTemporaryAbsence
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.ScheduledTemporaryAbsenceReturn
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.TemporaryAbsence
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.TemporaryAbsenceApplication
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.TemporaryAbsenceApplicationOutsideMovement
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.TemporaryAbsenceApplicationOutsideMovementResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.TemporaryAbsenceApplicationResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.TemporaryAbsenceReturn
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import java.time.LocalDateTime

@Component
class ExternalMovementsNomisApiMockServer(private val objectMapper: ObjectMapper) {
  private val now = LocalDateTime.now()
  private val yesterday = now.minusDays(1)
  private val tomorrow = now.plusDays(1)

  fun stubGetTemporaryAbsences(
    offenderNo: String = "A1234BC",
    response: OffenderTemporaryAbsencesResponse = temporaryAbsencesResponse(),
  ) {
    nomisApi.stubFor(
      get(urlPathEqualTo("/prisoners/$offenderNo/temporary-absences")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(objectMapper.writeValueAsString(response)),
      ),
    )
  }

  fun verifyGetTemporaryAbsences(offenderNo: String = "A1234BC", count: Int = 1) {
    nomisApi.verify(
      count,
      getRequestedFor(urlPathEqualTo("/prisoners/$offenderNo/temporary-absences")),
    )
  }

  fun stubGetTemporaryAbsences(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    nomisApi.stubFor(
      get(urlPathMatching("/prisoners/.*/temporary-absences")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun temporaryAbsencesResponse(): OffenderTemporaryAbsencesResponse = OffenderTemporaryAbsencesResponse(
    bookings = listOf(
      BookingTemporaryAbsences(
        bookingId = 12345,
        temporaryAbsenceApplications = listOf(
          TemporaryAbsenceApplication(
            movementApplicationId = 1,
            eventSubType = "C5",
            applicationDate = now.toLocalDate(),
            fromDate = now.toLocalDate(),
            releaseTime = now,
            toDate = tomorrow.toLocalDate(),
            returnTime = tomorrow,
            applicationStatus = "APP-SCH",
            applicationType = "SINGLE",
            escortCode = "U",
            transportType = "VAN",
            comment = "application comment",
            prisonId = "LEI",
            toAgencyId = "COURT1",
            toAddressId = 321,
            toAddressOwnerClass = "OFF",
            contactPersonName = "Jeff",
            temporaryAbsenceType = "RR",
            temporaryAbsenceSubType = "SPL",
            outsideMovements = listOf(outsideMovement()),
            absences = listOf(
              Absence(
                scheduledTemporaryAbsence = scheduledAbsence(),
                scheduledTemporaryAbsenceReturn = scheduledAbsenceReturn(),
                temporaryAbsence = absence().copy(sequence = 3, movementDate = now.toLocalDate(), movementTime = now),
                temporaryAbsenceReturn = absenceReturn().copy(
                  sequence = 4,
                  movementDate = now.toLocalDate(),
                  movementTime = now,
                ),
              ),
            ),
            audit = NomisAudit(
              createDatetime = now,
              createUsername = "USER",
            ),
          ),
        ),
        unscheduledTemporaryAbsences = listOf(
          absence().copy(sequence = 1, movementDate = yesterday.toLocalDate(), movementTime = yesterday),
        ),
        unscheduledTemporaryAbsenceReturns = listOf(
          absenceReturn().copy(sequence = 2, movementDate = yesterday.toLocalDate(), movementTime = yesterday),
        ),
      ),
    ),
  )

  private fun absenceReturn() = TemporaryAbsenceReturn(
    sequence = 2,
    movementDate = now.toLocalDate(),
    movementTime = now,
    movementReason = "C5",
    escort = "PECS",
    escortText = "Return escort text",
    fromAgency = "COURT1",
    toPrison = "LEI",
    commentText = "Return comment text",
    fromAddressId = 321L,
    fromAddressOwnerClass = "OFF",
    audit = NomisAudit(
      createDatetime = now,
      createUsername = "USER",
    ),
  )

  private fun absence() = TemporaryAbsence(
    sequence = 1,
    movementDate = now.toLocalDate(),
    movementTime = now,
    movementReason = "C6",
    arrestAgency = "POL",
    escort = "U",
    escortText = "Absence escort text",
    fromPrison = "LEI",
    toAgency = "COURT1",
    commentText = "Absence comment text",
    toAddressId = 321L,
    toAddressOwnerClass = "OFF",
    audit = NomisAudit(
      createDatetime = now,
      createUsername = "USER",
    ),
  )

  private fun scheduledAbsenceReturn() = ScheduledTemporaryAbsenceReturn(
    eventId = 2,
    eventSubType = "C5",
    eventStatus = "SCH",
    escort = "PECS",
    eventDate = tomorrow.toLocalDate(),
    startTime = tomorrow,
    comment = "scheduled return comment",
    fromAgency = "COURT1",
    toPrison = "LEI",
    audit = NomisAudit(
      createDatetime = now,
      createUsername = "USER",
    ),
  )

  private fun scheduledAbsence(): ScheduledTemporaryAbsence = ScheduledTemporaryAbsence(
    eventId = 1,
    eventSubType = "C5",
    eventStatus = "SCH",
    escort = "PECS",
    applicationTime = now,
    applicationDate = now,
    eventDate = now.toLocalDate(),
    startTime = now,
    returnDate = tomorrow.toLocalDate(),
    returnTime = tomorrow,
    comment = "scheduled absence comment",
    fromPrison = "LEI",
    toAgency = "COURT1",
    transportType = "VAN",
    toAddressId = 321L,
    toAddressOwnerClass = "OFF",
    audit = NomisAudit(
      createDatetime = now,
      createUsername = "USER",
    ),
  )

  private fun outsideMovement(): TemporaryAbsenceApplicationOutsideMovement = TemporaryAbsenceApplicationOutsideMovement(
    outsideMovementId = 1,
    eventSubType = "C5",
    fromDate = now.toLocalDate(),
    releaseTime = now,
    toDate = tomorrow.toLocalDate(),
    returnTime = tomorrow.plusDays(1),
    temporaryAbsenceType = "RR",
    temporaryAbsenceSubType = "SPL",
    comment = "outside movement comment",
    toAgencyId = "COURT1",
    toAddressId = 321L,
    toAddressOwnerClass = "OFF",
    contactPersonName = "Jeff",
    audit = NomisAudit(
      createDatetime = now,
      createUsername = "USER",
    ),
  )

  fun stubGetTemporaryAbsenceApplication(
    offenderNo: String = "A1234BC",
    applicationId: Long = 12345L,
    response: TemporaryAbsenceApplicationResponse = temporaryAbsenceApplicationResponse(),
  ) {
    nomisApi.stubFor(
      get(urlPathEqualTo("/prisoners/$offenderNo/temporary-absences/application/$applicationId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(objectMapper.writeValueAsString(response)),
      ),
    )
  }

  fun stubGetTemporaryAbsenceApplication(
    status: HttpStatus,
    error: ErrorResponse = ErrorResponse(status = status.value()),
  ) {
    nomisApi.stubFor(
      get(urlPathMatching("/prisoners/.*/temporary-absences/application/.*")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun temporaryAbsenceApplicationResponse() = TemporaryAbsenceApplicationResponse(
    bookingId = 12345,
    movementApplicationId = 111,
    eventSubType = "C5",
    applicationDate = now.toLocalDate(),
    fromDate = now.toLocalDate(),
    releaseTime = now,
    toDate = tomorrow.toLocalDate(),
    returnTime = tomorrow,
    applicationStatus = "APP-SCH",
    applicationType = "SINGLE",
    escortCode = "U",
    transportType = "VAN",
    comment = "application comment",
    prisonId = "LEI",
    toAgencyId = "COURT1",
    toAddressId = 321,
    toAddressOwnerClass = "OFF",
    contactPersonName = "Jeff",
    temporaryAbsenceType = "RR",
    temporaryAbsenceSubType = "SPL",
    audit = NomisAudit(
      createDatetime = now,
      createUsername = "USER",
    ),
  )

  fun stubGetTemporaryAbsenceApplicationOutsideMovement(
    offenderNo: String = "A1234BC",
    appMultiId: Long = 12345L,
    response: TemporaryAbsenceApplicationOutsideMovementResponse = temporaryAbsenceApplicationOutsideMovementResponse(),
  ) {
    nomisApi.stubFor(
      get(urlPathEqualTo("/movements/$offenderNo/temporary-absences/outside-movement/$appMultiId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(objectMapper.writeValueAsString(response)),
      ),
    )
  }

  fun stubGetTemporaryAbsenceApplicationOutsideMovement(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    nomisApi.stubFor(
      get(urlPathMatching("/movements/.*/temporary-absences/outside-movement/.*")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun temporaryAbsenceApplicationOutsideMovementResponse() = TemporaryAbsenceApplicationOutsideMovementResponse(
    bookingId = 12345,
    movementApplicationId = 111,
    outsideMovementId = 222,
    eventSubType = "C5",
    fromDate = now.toLocalDate(),
    releaseTime = now,
    toDate = tomorrow.toLocalDate(),
    returnTime = tomorrow,
    temporaryAbsenceType = "RR",
    temporaryAbsenceSubType = "SPL",
    comment = "outside movement comment",
    toAgencyId = "COURT1",
    toAddressId = 321,
    toAddressOwnerClass = "OFF",
    contactPersonName = "Jeff",
    audit = NomisAudit(
      createDatetime = now,
      createUsername = "USER",
    ),
  )

  fun verify(pattern: RequestPatternBuilder) = nomisApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = nomisApi.verify(count, pattern)
}
