package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest

@SpringAPIServiceTest
@Import(OrganisationsNomisApiService::class, OrganisationsNomisApiMockServer::class)
class OrganisationsNomisApiServiceTest {
  @Autowired
  private lateinit var apiService: OrganisationsNomisApiService

  @Autowired
  private lateinit var mockServer: OrganisationsNomisApiMockServer

  @Nested
  inner class GetCorporateOrganisation {
    @Test
    fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetCorporateOrganisation(corporateId = 1234567)

      apiService.getCorporateOrganisation(nomisCorporateId = 1234567)

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubGetCorporateOrganisation(corporateId = 1234567)

      apiService.getCorporateOrganisation(nomisCorporateId = 1234567)

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/corporates/1234567")),
      )
    }

    @Test
    fun `will return corporate`() = runTest {
      mockServer.stubGetCorporateOrganisation(corporateId = 1234567, corporate = corporateOrganisation().copy(name = "Police"))

      val corporate = apiService.getCorporateOrganisation(nomisCorporateId = 1234567)

      assertThat(corporate.name).isEqualTo("Police")
    }
  }
}
