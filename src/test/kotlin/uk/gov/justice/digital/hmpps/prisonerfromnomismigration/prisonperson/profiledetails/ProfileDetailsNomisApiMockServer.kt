package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.profiledetails

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.BookingProfileDetailsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.PrisonerProfileDetailsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.ProfileDetailsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import java.time.LocalDateTime

@Component
class ProfileDetailsNomisApiMockServer(private val objectMapper: ObjectMapper) {
  fun stubGetProfileDetails(
    offenderNo: String = "A1234AA",
    response: PrisonerProfileDetailsResponse = PrisonerProfileDetailsResponse(
      offenderNo = offenderNo,
      bookings = listOf(
        BookingProfileDetailsResponse(
          bookingId = 1,
          startDateTime = "2024-02-03T12:34:56",
          endDateTime = "2024-10-21T12:34:56",
          latestBooking = true,
          profileDetails = listOf(
            ProfileDetailsResponse(
              type = "BUILD",
              code = "SLIM",
              createDateTime = LocalDateTime.now().toString(),
              createdBy = "A_USER",
              modifiedDateTime = LocalDateTime.now().toString(),
              modifiedBy = "ANOTHER_USER",
              auditModuleName = "NOMIS",
            ),
            ProfileDetailsResponse(
              type = "SHOESIZE",
              code = "8.5",
              createDateTime = LocalDateTime.now().toString(),
              createdBy = "A_USER",
              modifiedDateTime = LocalDateTime.now().toString(),
              modifiedBy = "ANOTHER_USER",
              auditModuleName = "NOMIS",
            ),
          ),
        ),
      ),
    ),
  ) = nomisApi.stubFor(
    get(urlEqualTo("/prisoners/$offenderNo/profile-details")).willReturn(
      aResponse()
        .withHeader("Content-Type", "application/json")
        .withStatus(HttpStatus.OK.value())
        .withBody(objectMapper.writeValueAsString(response)),
    ),
  )

  fun stubGetProfileDetails(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    nomisApi.stubFor(
      get(urlPathMatching("/prisoners/\\w+/profile-details")).willReturn(
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