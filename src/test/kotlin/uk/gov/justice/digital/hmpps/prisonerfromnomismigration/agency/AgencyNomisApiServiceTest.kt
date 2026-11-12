package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.agency

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension

@ExtendWith(NomisApiExtension::class)
@SpringAPIServiceTest
@Import(AgencyNomisApiService::class, AgencyConfiguration::class, AgencyNomisApiMockServer::class)
class AgencyNomisApiServiceTest {
  @Autowired
  private lateinit var apiService: AgencyNomisApiService

  @Autowired
  private lateinit var mockServer: AgencyNomisApiMockServer

  @Nested
  inner class GetAgency {
    @Test
    internal fun `will pass oauth2 token to endpoint`() = runTest {
      mockServer.stubGetAgency(
        agencyId = "SHEFCC",
      )

      apiService.getAgency(
        agencyId = "SHEFCC",
      )

      mockServer.verify(
        getRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the get agency endpoint`() = runTest {
      mockServer.stubGetAgency(
        agencyId = "SHEFCC",
      )

      apiService.getAgency(
        agencyId = "SHEFCC",
      )
      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/agency/SHEFCC")),
      )
    }
  }

  @Nested
  inner class GetAgencyPhoneNumber {
    @Test
    internal fun `will pass oauth2 token to endpoint`() = runTest {
      mockServer.stubGetAgencyPhoneNumber(agencyId = "MDI")

      apiService.getAgencyPhoneNumber(agencyId = "MDI")

      mockServer.verify(
        getRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the get agency phone number endpoint`() = runTest {
      mockServer.stubGetAgencyPhoneNumber(agencyId = "MDI")

      apiService.getAgencyPhoneNumber(agencyId = "MDI")

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/agency/MDI/phone-number")),
      )
    }

    @Test
    fun `will return the phone number`() = runTest {
      val response = AgencyNomisApiMockServer.agencyPhoneResponse()
      mockServer.stubGetAgencyPhoneNumber(agencyId = "MDI", response = response)

      assertThat(apiService.getAgencyPhoneNumber(agencyId = "MDI")).isEqualTo(response)
    }
  }

  @Nested
  inner class GetAgencyIds {
    @Test
    internal fun `will pass oauth2 token to endpoint`() = runTest {
      mockServer.stubGetAgencyIds()

      apiService.getAgencyIds()

      mockServer.verify(
        getRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the get agency ids endpoint but exclude prisons`() = runTest {
      mockServer.stubGetAgencyIds()

      apiService.getAgencyIds()

      mockServer.verify(
        getRequestedFor(urlEqualTo("/agency/ids/all?excludeType=INST")),
      )
    }
  }
}
