package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.OrganisationsDpsApiExtension.Companion.dpsOrganisationsServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.OrganisationsDpsApiMockServer.Companion.migrateOrganisationRequest

@SpringAPIServiceTest
@Import(OrganisationsDpsApiService::class, OrganisationsConfiguration::class, OrganisationsDpsApiMockServer::class)
class OrganisationsDpsApiServiceTest {
  @Autowired
  private lateinit var apiService: OrganisationsDpsApiService

  @Nested
  inner class MigrateContact {
    @Test
    internal fun `will pass oath2 token to contact endpoint`() = runTest {
      dpsOrganisationsServer.stubMigrateOrganisation()

      apiService.migrateOrganisation(migrateOrganisationRequest())

      dpsOrganisationsServer.verify(
        postRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the migrate endpoint`() = runTest {
      dpsOrganisationsServer.stubMigrateOrganisation()

      apiService.migrateOrganisation(migrateOrganisationRequest())

      dpsOrganisationsServer.verify(
        postRequestedFor(urlPathEqualTo("/migrate/organisation")),
      )
    }
  }
}
