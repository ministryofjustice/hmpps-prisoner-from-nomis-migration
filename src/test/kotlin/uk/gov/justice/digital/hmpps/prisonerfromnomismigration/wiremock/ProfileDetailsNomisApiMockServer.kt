package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.havingExactly
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.BookingProfileDetailsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerProfileDetailsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.ProfileDetailsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.profiledetails.ContactPersonProfileType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import java.time.LocalDateTime

@Component
class ProfileDetailsNomisApiMockServer(private val jsonMapper: JsonMapper) {
  fun stubGetProfileDetails(
    offenderNo: String = "A1234AA",
    bookingId: Long? = 12345,
    profileTypes: List<String> = ContactPersonProfileType.Companion.all(),
    response: PrisonerProfileDetailsResponse = profileDetailsResponse(offenderNo),
  ) {
    nomisApi.stubFor(
      get(urlPathMatching("/prisoners/$offenderNo/profile-details"))
        .apply { bookingId?.run { withQueryParam("bookingId", equalTo(bookingId.toString())) } }
        .withQueryParam("profileTypes", havingExactly(*profileTypes.toTypedArray()))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(jsonMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubGetProfileDetails(
    offenderNo: String = "A1234AA",
    status: HttpStatus,
    error: ErrorResponse = ErrorResponse(status = status.value()),
  ) {
    nomisApi.stubFor(
      get(urlPathMatching("/prisoners/$offenderNo/profile-details")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = nomisApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = nomisApi.verify(count, pattern)
}

fun profileDetailsResponse(offenderNo: String, bookings: List<BookingProfileDetailsResponse> = listOf(booking())) = PrisonerProfileDetailsResponse(
  offenderNo = offenderNo,
  bookings = bookings,
)

fun booking(bookingId: Long = 1, latestBooking: Boolean = true, profileDetails: List<ProfileDetailsResponse> = listOf(profileDetails())) = BookingProfileDetailsResponse(
  bookingId = bookingId,
  startDateTime = LocalDateTime.parse("2024-02-03T12:34:56"),
  latestBooking = latestBooking,
  sequence = if (latestBooking) {
    1
  } else {
    2
  },
  profileDetails = profileDetails,
)

fun profileDetails(type: String = "MARITAL", code: String? = "M") = ProfileDetailsResponse(
  type = type,
  code = code,
  createDateTime = LocalDateTime.now(),
  createdBy = "A_USER",
  modifiedDateTime = LocalDateTime.now(),
  modifiedBy = "ANOTHER_USER",
  auditModuleName = "NOMIS",
)
