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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CorporateOrganisationIdResponse
import java.time.LocalDate

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

  @Nested
  inner class GetCorporateOrganisationIdsToMigrate {
    @Test
    fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetCorporateOrganisationIdsToMigrate()

      apiService.getCorporateOrganisationIdsToMigrate()

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will pass mandatory parameters to service`() = runTest {
      mockServer.stubGetCorporateOrganisationIdsToMigrate()

      apiService.getCorporateOrganisationIdsToMigrate(pageNumber = 12, pageSize = 20)

      mockServer.verify(
        getRequestedFor(anyUrl())
          .withQueryParam("fromDate", equalTo(""))
          .withQueryParam("toDate", equalTo(""))
          .withQueryParam("page", equalTo("12"))
          .withQueryParam("size", equalTo("20")),
      )
    }

    @Test
    fun `can pass optional parameters to service`() = runTest {
      mockServer.stubGetCorporateOrganisationIdsToMigrate()

      apiService.getCorporateOrganisationIdsToMigrate(
        fromDate = LocalDate.parse("2020-01-01"),
        toDate = LocalDate.parse("2020-01-02"),
        pageNumber = 12,
        pageSize = 20,
      )

      mockServer.verify(
        getRequestedFor(anyUrl())
          .withQueryParam("fromDate", equalTo("2020-01-01"))
          .withQueryParam("toDate", equalTo("2020-01-02")),
      )
    }

    @Test
    fun `will return corporate ids`() = runTest {
      mockServer.stubGetCorporateOrganisationIdsToMigrate(content = listOf(CorporateOrganisationIdResponse(1234567), CorporateOrganisationIdResponse(1234568)))

      val pages = apiService.getCorporateOrganisationIdsToMigrate(pageNumber = 12, pageSize = 20)

      assertThat(pages.content).hasSize(2)
      assertThat(pages.content[0].corporateId).isEqualTo(1234567)
      assertThat(pages.content[1].corporateId).isEqualTo(1234568)
    }
  }
}
