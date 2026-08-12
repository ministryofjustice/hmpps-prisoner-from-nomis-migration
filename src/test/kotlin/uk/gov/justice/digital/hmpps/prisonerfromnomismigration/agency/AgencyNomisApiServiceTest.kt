package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.agency

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
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
}
