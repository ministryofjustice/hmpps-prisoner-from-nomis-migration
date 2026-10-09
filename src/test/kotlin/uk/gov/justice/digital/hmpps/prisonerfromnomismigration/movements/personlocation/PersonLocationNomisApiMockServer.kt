package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.personlocation

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.BookingPersonLocations
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.MovementLocation
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.NomisAudit
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderPersonLocationsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PersonLocationMovement
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import java.time.LocalDateTime

@Component
class PersonLocationNomisApiMockServer(private val jsonMapper: JsonMapper) {

  fun stubGetOffenderPersonLocations(
    offenderNo: String = "A1234BC",
    response: OffenderPersonLocationsResponse = offenderPersonLocationsResponse(),
    status: HttpStatus = HttpStatus.OK,
    error: ErrorResponse = ErrorResponse(status = status.value()),
  ) {
    nomisApi.stubFor(
      get(urlPathEqualTo("/movements/$offenderNo/person-locations")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(if (status == HttpStatus.OK) response else error)),
      ),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = nomisApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = nomisApi.verify(count, pattern)
}

fun offenderPersonLocationsResponse(
  bookingId: Long = 12345L,
  bookingBeginTime: LocalDateTime = LocalDateTime.parse("2024-01-01T10:00:00"),
) = OffenderPersonLocationsResponse(
  bookings = listOf(
    BookingPersonLocations(
      bookingId = bookingId,
      bookingNumber = "B12345",
      activeBooking = true,
      latestBooking = true,
      bookingStatus = BookingPersonLocations.BookingStatus.OPEN,
      bookingBeginTime = bookingBeginTime,
      movements = listOf(
        PersonLocationMovement(
          sequence = 1,
          movementType = PersonLocationMovement.MovementType.ADM,
          directionCode = PersonLocationMovement.DirectionCode.IN,
          movementTime = bookingBeginTime,
          movementReason = CodeDescription(code = "N", description = "Unconvicted Remand"),
          audit = NomisAudit(createDatetime = bookingBeginTime, createUsername = "SOME_USER"),
          from = MovementLocation(type = MovementLocation.Type.COURT, description = "Leeds Court", code = "LEEDCC"),
          to = MovementLocation(type = MovementLocation.Type.PRISON, description = "Leeds (HMP)", code = "LEI"),
        ),
        PersonLocationMovement(
          sequence = 2,
          movementType = PersonLocationMovement.MovementType.TRN,
          directionCode = PersonLocationMovement.DirectionCode.OUT,
          movementTime = bookingBeginTime.plusDays(10),
          movementReason = CodeDescription(code = "NOTR", description = "Normal Transfer"),
          audit = NomisAudit(createDatetime = bookingBeginTime.plusDays(10), createUsername = "SOME_USER"),
          scheduleId = 54321L,
          commentText = "Transfer comment",
        ),
      ),
      audit = NomisAudit(createDatetime = bookingBeginTime, createUsername = "SOME_USER"),
    ),
  ),
)
