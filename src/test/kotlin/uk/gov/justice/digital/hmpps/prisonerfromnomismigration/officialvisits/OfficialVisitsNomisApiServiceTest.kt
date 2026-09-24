package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits

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
@Import(OfficialVisitsNomisApiService::class, OfficialVisitsConfiguration::class, OfficialVisitsNomisApiMockServer::class)
class OfficialVisitsNomisApiServiceTest {
  @Autowired
  private lateinit var apiService: OfficialVisitsNomisApiService

  @Autowired
  private lateinit var mockServer: OfficialVisitsNomisApiMockServer

  @Nested
  inner class GetOfficialVisit {
    @Test
    internal fun `will pass oauth2 token to endpoint`() = runTest {
      mockServer.stubGetOfficialVisit(
        visitId = 1234,
      )

      apiService.getOfficialVisit(
        visitId = 1234,
      )

      mockServer.verify(
        getRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the get time slot endpoint`() = runTest {
      mockServer.stubGetOfficialVisit(
        visitId = 1234,
      )

      apiService.getOfficialVisit(
        visitId = 1234,
      )
      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/official-visits/1234")),
      )
    }
  }

  @Nested
  inner class GetOfficialVisitsForPrisoner {
    @Test
    internal fun `will pass oauth2 token to endpoint`() = runTest {
      mockServer.stubGetOfficialVisitsForPrisoner(
        offenderNo = "A1234KT",
      )

      apiService.getOfficialVisitsForPrisoner(
        offenderNo = "A1234KT",
      )

      mockServer.verify(
        getRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the get visits endpoint`() = runTest {
      mockServer.stubGetOfficialVisitsForPrisoner(
        offenderNo = "A1234KT",
      )

      apiService.getOfficialVisitsForPrisoner(
        offenderNo = "A1234KT",
      )
      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/prisoner/A1234KT/official-visits")),
      )
    }
  }
}
