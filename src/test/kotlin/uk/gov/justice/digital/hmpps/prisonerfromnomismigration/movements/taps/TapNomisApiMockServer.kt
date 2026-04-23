package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.taps

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.BookingTap
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.BookingTapApplication
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.BookingTapMovementIn
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.BookingTapMovementOut
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.BookingTapScheduleIn
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.BookingTapScheduleOut
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.BookingTaps
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.NomisAudit
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderTapsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.TapApplication
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.TapMovementIn
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.TapMovementOut
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.TapScheduleOut
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import java.time.LocalDate
import java.time.LocalDateTime

@Component
class TapNomisApiMockServer(private val jsonMapper: JsonMapper) {

  fun stubGetAllOffenderTaps(
    offenderNo: String = "A1234BC",
    response: OffenderTapsResponse = offenderTapsResponse(),
  ) {
    nomisApi.stubFor(
      get(urlPathEqualTo("/movements/$offenderNo/taps")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(jsonMapper.writeValueAsString(response)),
      ),
    )
  }

  fun verifyGetAllOffenderTaps(offenderNo: String = "A1234BC", count: Int = 1) {
    nomisApi.verify(
      count,
      getRequestedFor(urlPathEqualTo("/movements/$offenderNo/taps")),
    )
  }

  fun stubGetAllOffenderTaps(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    nomisApi.stubFor(
      get(urlPathMatching("/movements/.*/taps")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubGetTapApplication(
    offenderNo: String = "A1234BC",
    applicationId: Long = 12345L,
    response: TapApplication = tapApplication(),
  ) {
    nomisApi.stubFor(
      get(urlPathEqualTo("/movements/$offenderNo/taps/application/$applicationId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(jsonMapper.writeValueAsString(response)),
      ),
    )
  }

  fun stubGetTapApplication(
    status: HttpStatus,
    error: ErrorResponse = ErrorResponse(status = status.value()),
  ) {
    nomisApi.stubFor(
      get(urlPathMatching("/movements/.*/taps/application/.*")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubGetTapScheduleOut(
    offenderNo: String = "A1234BC",
    eventId: Long = 12345L,
    eventTime: LocalDateTime = yesterday,
    applicationId: Long = 111L,
    addressOwnerClass: String = "OFF",
    eventStatus: String = "COMP",
    toAddress: String = "to full address",
    toAddressId: Long = 321,
    response: TapScheduleOut = tapScheduleOutResponse(
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
      get(urlPathEqualTo("/movements/$offenderNo/taps/schedule/out/$eventId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(jsonMapper.writeValueAsString(response)),
      ),
    )
  }

  fun stubGetTapScheduleOut(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    nomisApi.stubFor(
      get(urlPathMatching("/movements/.*/taps/schedule/out/.*")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubGetTapMovementOut(
    offenderNo: String = "A1234BC",
    bookingId: Long = 12345L,
    movementSeq: Int = 1,
    tapApplicationId: Long? = 111,
    tapScheduleOutId: Long? = 1,
    address: String = "full address",
    addressId: Long = 321,
    city: String? = null,
    response: TapMovementOut = tapMovementOut(tapApplicationId, tapScheduleOutId, movementSeq, address, addressId, city),
  ) {
    nomisApi.stubFor(
      get(urlPathEqualTo("/movements/$offenderNo/taps/movement/out/$bookingId/$movementSeq")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(jsonMapper.writeValueAsString(response)),
      ),
    )
  }

  fun stubGetTapMovementOut(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    nomisApi.stubFor(
      get(urlPathMatching("/movements/.*/taps/movement/out/.*/.*")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubGetTapMovementIn(
    offenderNo: String = "A1234BC",
    bookingId: Long = 12345L,
    movementSeq: Int = 1,
    tapApplicationId: Long? = 111,
    tapScheduleMovementInId: Long? = 2,
    tapScheduleMovementOutId: Long? = 1,
    address: String = "full address",
    addressId: Long = 321L,
    city: String? = null,
    response: TapMovementIn = tapMovementIn(tapApplicationId, tapScheduleMovementInId, movementSeq, tapScheduleMovementOutId, address, addressId, city),
  ) {
    nomisApi.stubFor(
      get(urlPathEqualTo("/movements/$offenderNo/taps/movement/in/$bookingId/$movementSeq")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(jsonMapper.writeValueAsString(response)),
      ),
    )
  }

  fun stubGetTapMovementIn(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    nomisApi.stubFor(
      get(urlPathMatching("/movements/.*/taps/movement/in/.*/.*")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = nomisApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = nomisApi.verify(count, pattern)

  companion object {
    private val now = LocalDateTime.now()
    private val yesterday = now.minusDays(1)
    private val tomorrow = now.plusDays(1)

    fun tapScheduleOutResponse(
      startTime: LocalDateTime = now,
      applicationId: Long = 111,
      eventId: Long = 1,
      addressOwnerClass: String = "OFF",
      eventStatus: String = "COMP",
      toAddress: String = "to full address",
      toAddressId: Long = 321,
    ) = TapScheduleOut(
      bookingId = 12345,
      tapApplicationId = applicationId,
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
      contactPersonName = "Derek",
      escort = "PECS",
      fromPrison = "LEI",
      toAgency = "COURT1",
      transportType = "VAN",
      tapAbsenceType = "RDR",
      tapSubType = "RR",
      toAddressId = toAddressId,
      toAddressOwnerClass = addressOwnerClass,
      toFullAddress = toAddress,
      toAddressDescription = "some description",
      toAddressPostcode = "S1 1AB",
      applicationTime = now,
      audit = NomisAudit(
        createDatetime = now,
        createUsername = "USER",
      ),
    )

    fun offenderTapsResponse(
      movementPrison: String = "LEI",
      bookingId: Long = 12345L,
      activeBooking: Boolean = true,
      latestBooking: Boolean = true,
      taps: List<BookingTap> = listOf(taps(movementPrison = movementPrison)),
      tapApplications: List<BookingTapApplication> = listOf(application(taps = taps)),
      unscheduledTapMovementOuts: List<BookingTapMovementOut> = listOf(tapMovementOut(seq = 1).copy(movementDate = yesterday.toLocalDate(), movementTime = yesterday)),
      unscheduledTapMovementIns: List<BookingTapMovementIn> = listOf(tapMovementIn(seq = 2).copy(movementDate = yesterday.toLocalDate(), movementTime = yesterday)),
    ): OffenderTapsResponse = OffenderTapsResponse(
      bookings = listOf(
        BookingTaps(
          bookingId = bookingId,
          activeBooking = activeBooking,
          latestBooking = latestBooking,
          tapApplications = tapApplications,
          unscheduledTapMovementOuts = unscheduledTapMovementOuts,
          unscheduledTapMovementIns = unscheduledTapMovementIns,
        ),
      ),
    )

    fun application(
      id: Long = 1,
      fromDate: LocalDate = now.toLocalDate(),
      toDate: LocalDate = tomorrow.toLocalDate(),
      status: String = "APP-SCH",
      taps: List<BookingTap> = listOf(taps(movementPrison = "LEI")),
    ) = BookingTapApplication(
      tapApplicationId = id,
      eventSubType = "C5",
      applicationDate = now.toLocalDate(),
      fromDate = fromDate,
      releaseTime = now,
      toDate = toDate,
      returnTime = tomorrow,
      applicationStatus = status,
      applicationType = "SINGLE",
      escortCode = "U",
      transportType = "VAN",
      comment = "application comment",
      prisonId = "LEI",
      toAgencyId = "COURT1",
      toAddressId = 321,
      toAddressOwnerClass = "OFF",
      toAddressDescription = "some address description",
      toFullAddress = "some full address",
      toAddressPostcode = "S1 1AA",
      contactPersonName = "Jeff",
      tapType = "RR",
      tapSubType = "SPL",
      taps = taps,
      audit = NomisAudit(
        createDatetime = now,
        createUsername = "USER",
      ),
    )

    fun taps(
      movementPrison: String = "LEI",
      tapScheduleOut: BookingTapScheduleOut = tapScheduleOut(),
      tapScheduleIn: BookingTapScheduleIn = tapScheduleIn(),
      tapMovementOut: BookingTapMovementOut = tapMovementOut(seq = 3).copy(
        movementDate = yesterday.toLocalDate(),
        movementTime = yesterday,
        fromPrison = movementPrison,
      ),
      tapMovementIn: BookingTapMovementIn = tapMovementIn(seq = 4).copy(
        movementDate = now.toLocalDate(),
        movementTime = now,
        toPrison = movementPrison,
      ),
    ) = BookingTap(tapScheduleOut, tapScheduleIn, tapMovementOut, tapMovementIn)

    fun tapMovementIn(seq: Int = 2) = BookingTapMovementIn(
      sequence = seq,
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

    fun tapMovementOut(seq: Int = 1) = BookingTapMovementOut(
      sequence = seq,
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

    fun tapScheduleIn(id: Long = 2) = BookingTapScheduleIn(
      eventId = id,
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

    fun tapScheduleOut(id: Long = 1): BookingTapScheduleOut = BookingTapScheduleOut(
      eventId = id,
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

    fun tapApplication(
      activeBooking: Boolean = true,
      latestBooking: Boolean = true,
      status: String = "APP-SCH",
      fromDate: LocalDate = now.toLocalDate(),
      toDate: LocalDate = tomorrow.toLocalDate(),
    ) = TapApplication(
      bookingId = 12345,
      activeBooking = activeBooking,
      latestBooking = latestBooking,
      tapApplicationId = 111,
      eventSubType = "C5",
      applicationDate = now.toLocalDate(),
      fromDate = fromDate,
      releaseTime = now,
      toDate = toDate,
      returnTime = tomorrow,
      applicationStatus = status,
      applicationType = "SINGLE",
      escortCode = "P",
      transportType = "VAN",
      comment = "application comment",
      prisonId = "LEI",
      toAgencyId = "COURT1",
      toAddressId = 321,
      toAddressOwnerClass = "OFF",
      toAddressDescription = "some address description",
      toFullAddress = "some full address",
      toAddressPostcode = "S1 1AA",
      contactPersonName = "Jeff",
      tapType = "RR",
      tapSubType = "SPL",
      audit = NomisAudit(
        createDatetime = now,
        createUsername = "USER",
      ),
    )

    fun tapMovementOut(
      tapApplicationId: Long? = 111,
      tapScheduleOutId: Long? = 1,
      sequence: Int = 1,
      address: String = "full address",
      addressId: Long = 321,
      city: String? = null,
    ) = TapMovementOut(
      bookingId = 12345,
      sequence = sequence,
      movementDate = now.toLocalDate(),
      movementTime = now,
      movementReason = "C6",
      audit = NomisAudit(
        createDatetime = now,
        createUsername = "USER",
      ),
      tapApplicationId = tapApplicationId,
      tapScheduleOutId = tapScheduleOutId,
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

    fun tapMovementIn(
      tapApplicationId: Long? = 111,
      tapScheduleInId: Long? = 2,
      sequence: Int = 1,
      tapScheduleOutId: Long? = 1,
      address: String = "full address",
      addressId: Long = 321L,
      city: String? = null,
    ) = TapMovementIn(
      bookingId = 12345,
      sequence = sequence,
      movementDate = now.toLocalDate(),
      movementTime = now,
      movementReason = "C5",
      audit = NomisAudit(
        createDatetime = now,
        createUsername = "USER",
      ),
      tapApplicationId = tapApplicationId,
      tapScheduleOutId = tapScheduleOutId,
      tapScheduleInId = tapScheduleInId,
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
  }
}
