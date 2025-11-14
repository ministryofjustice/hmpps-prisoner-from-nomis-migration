package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.profiledetails

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.havingExactly
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.tuple
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.ProfileDetailsNomisApiMockServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.booking
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.profileDetails
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.profileDetailsResponse
import java.time.LocalDate
import java.time.LocalDateTime

@SpringAPIServiceTest
@Import(ContactPersonProfileDetailsNomisApiService::class, ProfileDetailsNomisApiMockServer::class)
class ContactPersonProfileDetailsNomisApiServiceTest {
  @Autowired
  private lateinit var apiService: NomisApiService

  @Autowired
  private lateinit var profileDetailsNomisApi: ProfileDetailsNomisApiMockServer

  @Nested
  inner class GetProfileDetails {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      profileDetailsNomisApi.stubGetProfileDetails(
        offenderNo = "A1234AA",
        profileTypes = listOf("MARITAL", "CHILD"),
        bookingId = 12345,
      )

      apiService.getProfileDetails(offenderNo = "A1234AA", profileTypes = listOf("MARITAL", "CHILD"), bookingId = 12345)

      profileDetailsNomisApi.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass parameters to the service`() = runTest {
      profileDetailsNomisApi.stubGetProfileDetails(
        offenderNo = "A1234AA",
        profileTypes = listOf("MARITAL", "CHILD"),
        bookingId = 12345,
      )

      apiService.getProfileDetails(offenderNo = "A1234AA", profileTypes = listOf("MARITAL", "CHILD"), bookingId = 12345)

      profileDetailsNomisApi.verify(
        getRequestedFor(urlPathEqualTo("/prisoners/A1234AA/profile-details"))
          .withQueryParam("profileTypes", havingExactly("MARITAL", "CHILD"))
          .withQueryParam("bookingId", equalTo("12345")),

      )
    }

    @Test
    fun `will return profile details`() = runTest {
      profileDetailsNomisApi.stubGetProfileDetails(
        offenderNo = "A1234AA",
        profileTypes = listOf("MARITAL", "CHILD"),
        bookingId = 12345,
        response = profileDetailsResponse(
          offenderNo = "A1234AA",
          bookings = listOf(
            booking(
              profileDetails = listOf(
                profileDetails("MARITAL", "M"),
                profileDetails("CHILD", "3"),
              ),
            ),
          ),
        ),
      )

      val profileDetailsResponse = apiService.getProfileDetails(
        offenderNo = "A1234AA",
        profileTypes = listOf("MARITAL", "CHILD"),
        bookingId = 12345,
      )

      with(profileDetailsResponse) {
        assertThat(offenderNo).isEqualTo("A1234AA")
        assertThat(bookings)
          .extracting("bookingId", "startDateTime", "latestBooking")
          .containsExactly(tuple(1L, LocalDateTime.parse("2024-02-03T12:34:56"), true))
        assertThat(bookings[0].profileDetails)
          .extracting("type", "code", "createdBy", "modifiedBy", "auditModuleName")
          .containsExactly(
            tuple("MARITAL", "M", "A_USER", "ANOTHER_USER", "NOMIS"),
            tuple("CHILD", "3", "A_USER", "ANOTHER_USER", "NOMIS"),
          )
        assertThat(bookings[0].profileDetails[0].createDateTime.toLocalDate()).isEqualTo(LocalDate.now())
        assertThat(bookings[0].profileDetails[0].modifiedDateTime!!.toLocalDate()).isEqualTo(LocalDate.now())
      }
    }

    @Test
    fun `will throw error when bookings do not exist`() = runTest {
      profileDetailsNomisApi.stubGetProfileDetails(status = NOT_FOUND)

      assertThrows<WebClientResponseException.NotFound> {
        apiService.getProfileDetails(offenderNo = "A1234AA")
      }
    }

    @Test
    fun `will throw error when API returns an error`() = runTest {
      profileDetailsNomisApi.stubGetProfileDetails(status = INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.getProfileDetails(offenderNo = "A1234AA")
      }
    }
  }
}
