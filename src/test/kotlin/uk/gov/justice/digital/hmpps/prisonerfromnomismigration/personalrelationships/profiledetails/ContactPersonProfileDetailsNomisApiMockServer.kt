package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.profiledetails

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.havingExactly
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.BookingProfileDetailsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerProfileDetailsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.ProfileDetailsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import java.time.LocalDateTime

@Component
class ContactPersonProfileDetailsNomisApiMockServer(private val objectMapper: ObjectMapper) {
  fun stubGetProfileDetails(
    offenderNo: String = "A1234AA",
    bookingId: Long? = 12345,
    profileTypes: List<String> = ContactPersonProfileType.all(),
    response: PrisonerProfileDetailsResponse = PrisonerProfileDetailsResponse(
      offenderNo = offenderNo,
      bookings = listOf(
        BookingProfileDetailsResponse(
          bookingId = 1,
          startDateTime = LocalDateTime.parse("2024-02-03T12:34:56"),
          latestBooking = true,
          profileDetails = listOf(
            ProfileDetailsResponse(
              type = "MARITAL",
              code = "M",
              createDateTime = LocalDateTime.now(),
              createdBy = "A_USER",
              modifiedDateTime = LocalDateTime.now(),
              modifiedBy = "ANOTHER_USER",
              auditModuleName = "NOMIS",
            ),
            ProfileDetailsResponse(
              type = "CHILD",
              code = "3",
              createDateTime = LocalDateTime.now(),
              createdBy = "A_USER",
              modifiedDateTime = LocalDateTime.now(),
              modifiedBy = "ANOTHER_USER",
              auditModuleName = "NOMIS",
            ),
          ),
        ),
      ),
    ),
  ) = nomisApi.stubFor(
    get(urlPathMatching("/prisoners/$offenderNo/profile-details"))
      .apply { bookingId?.run { withQueryParam("bookingId", equalTo(bookingId.toString())) } }
      .withQueryParam("profileTypes", havingExactly(*profileTypes.toTypedArray()))
      .willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(objectMapper.writeValueAsString(response)),
      ),
  )

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
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = nomisApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = nomisApi.verify(count, pattern)
}
