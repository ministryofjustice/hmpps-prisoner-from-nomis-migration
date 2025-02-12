package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.OrganisationsDpsApiExtension.Companion.dpsOrganisationsServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.OrganisationsDpsApiMockServer.Companion.migrateOrganisationRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.OrganisationsDpsApiMockServer.Companion.syncCreateOrganisationAddressRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.OrganisationsDpsApiMockServer.Companion.syncCreateOrganisationRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.OrganisationsDpsApiMockServer.Companion.syncUpdateOrganisationAddressRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.OrganisationsDpsApiMockServer.Companion.syncUpdateOrganisationRequest

@SpringAPIServiceTest
@Import(OrganisationsDpsApiService::class, OrganisationsConfiguration::class, OrganisationsDpsApiMockServer::class)
class OrganisationsDpsApiServiceTest {
  @Autowired
  private lateinit var apiService: OrganisationsDpsApiService

  @Nested
  inner class MigrateOrganisation {
    @Test
    internal fun `will pass oath2 token to organisation endpoint`() = runTest {
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

  @Nested
  inner class CreateOrganisation {
    @Test
    internal fun `will pass oath2 token to organisation endpoint`() = runTest {
      dpsOrganisationsServer.stubCreateOrganisation()

      apiService.createOrganisation(syncCreateOrganisationRequest())

      dpsOrganisationsServer.verify(
        postRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the POST endpoint`() = runTest {
      dpsOrganisationsServer.stubCreateOrganisation()

      apiService.createOrganisation(syncCreateOrganisationRequest())

      dpsOrganisationsServer.verify(
        postRequestedFor(urlPathEqualTo("/sync/organisation")),
      )
    }
  }

  @Nested
  inner class UpdateOrganisation {
    @Test
    internal fun `will pass oath2 token to organisation endpoint`() = runTest {
      dpsOrganisationsServer.stubUpdateOrganisation(12345)

      apiService.updateOrganisation(12345, syncUpdateOrganisationRequest())

      dpsOrganisationsServer.verify(
        putRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the PUT endpoint`() = runTest {
      dpsOrganisationsServer.stubUpdateOrganisation(12345)

      apiService.updateOrganisation(12345, syncUpdateOrganisationRequest())

      dpsOrganisationsServer.verify(
        putRequestedFor(urlPathEqualTo("/sync/organisation/12345")),
      )
    }
  }

  @Nested
  inner class DeleteOrganisation {
    @Test
    internal fun `will pass oath2 token to organisation endpoint`() = runTest {
      dpsOrganisationsServer.stubDeleteOrganisation(12345)

      apiService.deleteOrganisation(12345)

      dpsOrganisationsServer.verify(
        deleteRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the DELETE endpoint`() = runTest {
      dpsOrganisationsServer.stubDeleteOrganisation(12345)

      apiService.deleteOrganisation(12345)

      dpsOrganisationsServer.verify(
        deleteRequestedFor(urlPathEqualTo("/sync/organisation/12345")),
      )
    }
  }

  @Nested
  inner class CreateOrganisationAddress {
    @Test
    internal fun `will pass oath2 token to organisation address endpoint`() = runTest {
      dpsOrganisationsServer.stubCreateOrganisationAddress()

      apiService.createOrganisationAddress(syncCreateOrganisationAddressRequest())

      dpsOrganisationsServer.verify(
        postRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the POST endpoint`() = runTest {
      dpsOrganisationsServer.stubCreateOrganisationAddress()

      apiService.createOrganisationAddress(syncCreateOrganisationAddressRequest())

      dpsOrganisationsServer.verify(
        postRequestedFor(urlPathEqualTo("/sync/organisation-address")),
      )
    }
  }

  @Nested
  inner class UpdateOrganisationAddress {
    @Test
    internal fun `will pass oath2 token to organisation-address endpoint`() = runTest {
      dpsOrganisationsServer.stubUpdateOrganisationAddress(12345)

      apiService.updateOrganisationAddress(12345, syncUpdateOrganisationAddressRequest())

      dpsOrganisationsServer.verify(
        putRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the PUT endpoint`() = runTest {
      dpsOrganisationsServer.stubUpdateOrganisationAddress(12345)

      apiService.updateOrganisationAddress(12345, syncUpdateOrganisationAddressRequest())

      dpsOrganisationsServer.verify(
        putRequestedFor(urlPathEqualTo("/sync/organisation-address/12345")),
      )
    }
  }

  @Nested
  inner class DeleteOrganisationAddress {
    @Test
    internal fun `will pass oath2 token to organisation-address endpoint`() = runTest {
      dpsOrganisationsServer.stubDeleteOrganisationAddress(12345)

      apiService.deleteOrganisationAddress(12345)

      dpsOrganisationsServer.verify(
        deleteRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the DELETE endpoint`() = runTest {
      dpsOrganisationsServer.stubDeleteOrganisationAddress(12345)

      apiService.deleteOrganisationAddress(12345)

      dpsOrganisationsServer.verify(
        deleteRequestedFor(urlPathEqualTo("/sync/organisation-address/12345")),
      )
    }
  }
}
