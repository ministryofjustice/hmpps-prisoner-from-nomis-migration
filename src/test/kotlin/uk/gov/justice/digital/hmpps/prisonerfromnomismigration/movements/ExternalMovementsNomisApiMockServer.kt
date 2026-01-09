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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.ScheduledTemporaryAbsenceResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.ScheduledTemporaryAbsenceReturn
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.ScheduledTemporaryAbsenceReturnResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.TemporaryAbsence
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.TemporaryAbsenceApplication
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.TemporaryAbsenceApplicationResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.TemporaryAbsenceResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.TemporaryAbsenceReturn
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.TemporaryAbsenceReturnResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import java.time.LocalDateTime

@Component
class ExternalMovementsNomisApiMockServer(private val objectMapper: ObjectMapper) {

  fun stubGetTemporaryAbsences(
    offenderNo: String = "A1234BC",
    response: OffenderTemporaryAbsencesResponse = temporaryAbsencesResponse(),
  ) {
    nomisApi.stubFor(
      get(urlPathEqualTo("/movements/$offenderNo/temporary-absences")).willReturn(
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
      getRequestedFor(urlPathEqualTo("/movements/$offenderNo/temporary-absences")),
    )
  }

  fun stubGetTemporaryAbsences(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    nomisApi.stubFor(
      get(urlPathMatching("/movements/.*/temporary-absences")).willReturn(
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
            absences = listOf(
              Absence(
                scheduledTemporaryAbsence = scheduledAbsence(),
                scheduledTemporaryAbsenceReturn = scheduledAbsenceReturn(),
                temporaryAbsence = absence().copy(sequence = 3, movementDate = yesterday.toLocalDate(), movementTime = yesterday),
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
    fromAddressOwnerClass = "CORP",
    fromAddressDescription = "Absence return address description",
    fromFullAddress = "Absence return full address",
    fromAddressPostcode = "S2 2AA",
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
    toAddressId = 432L,
    toAddressOwnerClass = "AGY",
    toAddressDescription = "Absence address description",
    toFullAddress = "Absence full address",
    toAddressPostcode = "S1 1AA",
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
    eventDate = yesterday.toLocalDate(),
    startTime = yesterday,
    returnDate = tomorrow.toLocalDate(),
    returnTime = tomorrow,
    comment = "scheduled absence comment",
    fromPrison = "LEI",
    toAgency = "COURT1",
    transportType = "VAN",
    toAddressId = 543L,
    toAddressOwnerClass = "CORP",
    toAddressDescription = "Schedule address description",
    toFullAddress = "Schedule full address",
    toAddressPostcode = "S1 1AA",
    contactPersonName = "Derek",
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
      get(urlPathEqualTo("/movements/$offenderNo/temporary-absences/application/$applicationId")).willReturn(
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
      get(urlPathMatching("/movements/.*/temporary-absences/application/.*")).willReturn(
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
    escortCode = "P",
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

  fun stubGetTemporaryAbsenceScheduledMovement(
    offenderNo: String = "A1234BC",
    eventId: Long = 12345L,
    eventTime: LocalDateTime = yesterday,
    applicationId: Long = 111L,
    addressOwnerClass: String = "OFF",
    eventStatus: String = "COMP",
    toAddress: String = "to full address",
    toAddressId: Long = 321,
    response: ScheduledTemporaryAbsenceResponse = scheduledTemporaryAbsenceResponse(
      startTime = eventTime,
      applicationId = applicationId,
      eventId = eventId,
      addressOwnerClass = addressOwnerClass,
      eventStatus = eventStatus,
      toAddress = toAddress,
      toAddressId = toAddressId,
    ),
  ) {
    nomisApi.stubFor(
      get(urlPathEqualTo("/movements/$offenderNo/temporary-absences/scheduled-temporary-absence/$eventId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(objectMapper.writeValueAsString(response)),
      ),
    )
  }

  fun stubGetTemporaryAbsenceScheduledMovement(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    nomisApi.stubFor(
      get(urlPathMatching("/movements/.*/temporary-absences/scheduled-temporary-absence/.*")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubGetTemporaryAbsenceScheduledReturnMovement(
    offenderNo: String = "A1234BC",
    eventId: Long = 23456L,
    parentEventId: Long = 12345L,
    response: ScheduledTemporaryAbsenceReturnResponse = scheduledTemporaryAbsenceReturnResponse(parentEventId),
  ) {
    nomisApi.stubFor(
      get(urlPathEqualTo("/movements/$offenderNo/temporary-absences/scheduled-temporary-absence-return/$eventId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(objectMapper.writeValueAsString(response)),
      ),
    )
  }

  fun stubGetTemporaryAbsenceScheduledReturnMovement(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    nomisApi.stubFor(
      get(urlPathMatching("/movements/.*/temporary-absences/scheduled-temporary-absence-return/.*")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun scheduledTemporaryAbsenceReturnResponse(parentEventId: Long = 1) = ScheduledTemporaryAbsenceReturnResponse(
    bookingId = 12345,
    movementApplicationId = 111,
    eventId = 2,
    parentEventId = parentEventId,
    eventSubType = "C5",
    eventStatus = "SCH",
    eventDate = tomorrow.toLocalDate(),
    startTime = tomorrow,
    comment = "scheduled return comment",
    escort = "PECS",
    fromAgency = "COURT1",
    toPrison = "LEI",
    audit = NomisAudit(
      createDatetime = now,
      createUsername = "USER",
    ),
  )

  fun stubGetTemporaryAbsenceMovement(
    offenderNo: String = "A1234BC",
    bookingId: Long = 12345L,
    movementSeq: Int = 1,
    movementApplicationId: Long? = 111,
    scheduledTemporaryAbsenceId: Long? = 1,
    address: String = "full address",
    addressId: Long = 321,
    city: String? = null,
    response: TemporaryAbsenceResponse = temporaryAbsenceResponse(movementApplicationId, scheduledTemporaryAbsenceId, movementSeq, address, addressId, city),
  ) {
    nomisApi.stubFor(
      get(urlPathEqualTo("/movements/$offenderNo/temporary-absences/temporary-absence/$bookingId/$movementSeq")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(objectMapper.writeValueAsString(response)),
      ),
    )
  }

  fun stubGetTemporaryAbsenceMovement(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    nomisApi.stubFor(
      get(urlPathMatching("/movements/.*/temporary-absences/temporary-absence/.*/.*")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun temporaryAbsenceResponse(
    movementApplicationId: Long? = 111,
    scheduledTemporaryAbsenceId: Long? = 1,
    sequence: Int = 1,
    address: String = "full address",
    addressId: Long = 321,
    city: String? = null,
  ) = TemporaryAbsenceResponse(
    bookingId = 12345,
    sequence = sequence,
    movementDate = now.toLocalDate(),
    movementTime = now,
    movementReason = "C6",
    audit = NomisAudit(
      createDatetime = now,
      createUsername = "USER",
    ),
    movementApplicationId = movementApplicationId,
    scheduledTemporaryAbsenceId = scheduledTemporaryAbsenceId,
    arrestAgency = "POL",
    escort = "P",
    escortText = "Absence escort text",
    fromPrison = "LEI",
    toAgency = "COURT1",
    commentText = "Absence comment text",
    toAddressId = if (city == null) addressId else null,
    toAddressOwnerClass = if (city == null) "OFF" else null,
    toAddressDescription = if (city == null) "Some description" else null,
    toFullAddress = city ?: address,
    toAddressPostcode = if (city == null) "S1 1AB" else null,
  )

  fun stubGetTemporaryAbsenceReturnMovement(
    offenderNo: String = "A1234BC",
    bookingId: Long = 12345L,
    movementSeq: Int = 1,
    movementApplicationId: Long? = 111,
    scheduledTemporaryAbsenceReturnId: Long? = 2,
    scheduledTemporaryAbsenceId: Long? = 1,
    address: String = "full address",
    addressId: Long = 321L,
    city: String? = null,
    response: TemporaryAbsenceReturnResponse = temporaryAbsenceReturnResponse(movementApplicationId, scheduledTemporaryAbsenceReturnId, movementSeq, scheduledTemporaryAbsenceId, address, addressId, city),
  ) {
    nomisApi.stubFor(
      get(urlPathEqualTo("/movements/$offenderNo/temporary-absences/temporary-absence-return/$bookingId/$movementSeq")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(objectMapper.writeValueAsString(response)),
      ),
    )
  }

  fun stubGetTemporaryAbsenceReturnMovement(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    nomisApi.stubFor(
      get(urlPathMatching("/movements/.*/temporary-absences/temporary-absence-return/.*/.*")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun temporaryAbsenceReturnResponse(
    movementApplicationId: Long? = 111,
    scheduledTemporaryAbsenceReturnId: Long? = 2,
    sequence: Int = 1,
    scheduledTemporaryAbsenceId: Long? = 1,
    address: String = "full address",
    addressId: Long = 321L,
    city: String? = null,
  ) = TemporaryAbsenceReturnResponse(
    bookingId = 12345,
    sequence = sequence,
    movementDate = now.toLocalDate(),
    movementTime = now,
    movementReason = "C5",
    audit = NomisAudit(
      createDatetime = now,
      createUsername = "USER",
    ),
    movementApplicationId = movementApplicationId,
    scheduledTemporaryAbsenceId = scheduledTemporaryAbsenceId,
    scheduledTemporaryAbsenceReturnId = scheduledTemporaryAbsenceReturnId,
    escort = "PECS",
    escortText = "Return escort text",
    fromAgency = "COURT1",
    toPrison = "LEI",
    commentText = "Return comment text",
    fromAddressId = if (city == null) addressId else null,
    fromAddressOwnerClass = if (city == null) "OFF" else null,
    fromAddressDescription = if (city == null) "some description" else null,
    fromFullAddress = city ?: address,
    fromAddressPostcode = if (city == null) "S1 1AB" else null,
  )

  fun verify(pattern: RequestPatternBuilder) = nomisApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = nomisApi.verify(count, pattern)

  companion object {
    private val now = LocalDateTime.now()
    private val yesterday = now.minusDays(1)
    private val tomorrow = now.plusDays(1)

    fun scheduledTemporaryAbsenceResponse(
      startTime: LocalDateTime = now,
      applicationId: Long = 111,
      eventId: Long = 1,
      addressOwnerClass: String = "OFF",
      eventStatus: String = "COMP",
      toAddress: String = "to full address",
      toAddressId: Long = 321,
    ) = ScheduledTemporaryAbsenceResponse(
      bookingId = 12345,
      movementApplicationId = applicationId,
      eventId = eventId,
      eventSubType = "C5",
      eventStatus = eventStatus,
      inboundEventStatus = "SCH",
      returnDate = tomorrow.toLocalDate(),
      returnTime = tomorrow,
      applicationDate = now,
      eventDate = startTime.toLocalDate(),
      startTime = startTime,
      comment = "scheduled absence comment",
      escort = "PECS",
      fromPrison = "LEI",
      toAgency = "COURT1",
      transportType = "VAN",
      temporaryAbsenceType = "RDR",
      temporaryAbsenceSubType = "RR",
      toAddressId = toAddressId,
      toAddressOwnerClass = addressOwnerClass,
      toFullAddress = toAddress,
      toAddressDescription = "Some description",
      toAddressPostcode = "S1 1AB",
      applicationTime = now,
      audit = NomisAudit(
        createDatetime = now,
        createUsername = "USER",
      ),
    )
  }
}
