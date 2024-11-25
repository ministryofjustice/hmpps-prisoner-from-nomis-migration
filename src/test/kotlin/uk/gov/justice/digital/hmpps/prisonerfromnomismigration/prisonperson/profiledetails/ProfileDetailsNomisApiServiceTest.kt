package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.profiledetails

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
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
import java.time.LocalDate
import java.time.LocalDateTime

@SpringAPIServiceTest
@Import(ProfileDetailsNomisApiService::class, ProfileDetailsNomisApiMockServer::class)
class ProfileDetailsNomisApiServiceTest {
  @Autowired
  private lateinit var apiService: ProfileDetailsNomisApiService

  @Autowired
  private lateinit var profileDetailsNomisApi: ProfileDetailsNomisApiMockServer

  @Nested
  inner class GetProfileDetails {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      profileDetailsNomisApi.stubGetProfileDetails(offenderNo = "A1234AA")

      apiService.getProfileDetails(offenderNo = "A1234AA")

      profileDetailsNomisApi.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass NOMIS ids to service`() = runTest {
      profileDetailsNomisApi.stubGetProfileDetails(offenderNo = "A1234AA")

      apiService.getProfileDetails(offenderNo = "A1234AA")

      profileDetailsNomisApi.verify(
        getRequestedFor(urlPathEqualTo("/prisoners/A1234AA/profile-details")),
      )
    }

    @Test
    fun `will return physical attributes`() = runTest {
      profileDetailsNomisApi.stubGetProfileDetails(offenderNo = "A1234AA")

      val profileDetailsResponse = apiService.getProfileDetails("A1234AA")

      with(profileDetailsResponse) {
        assertThat(offenderNo).isEqualTo("A1234AA")
        assertThat(bookings)
          .extracting("bookingId", "startDateTime", "endDateTime", "latestBooking")
          .containsExactly(tuple(1L, "2024-02-03T12:34:56", "2024-10-21T12:34:56", true))
        assertThat(bookings[0].profileDetails)
          .extracting("type", "code", "createdBy", "modifiedBy", "auditModuleName")
          .containsExactly(
            tuple("BUILD", "SLIM", "A_USER", "ANOTHER_USER", "NOMIS"),
            tuple("SHOESIZE", "8.5", "A_USER", "ANOTHER_USER", "NOMIS"),
          )
        assertThat(LocalDateTime.parse(bookings[0].profileDetails[0].createDateTime).toLocalDate()).isEqualTo(LocalDate.now())
        assertThat(LocalDateTime.parse(bookings[0].profileDetails[0].modifiedDateTime!!).toLocalDate()).isEqualTo(LocalDate.now())
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
