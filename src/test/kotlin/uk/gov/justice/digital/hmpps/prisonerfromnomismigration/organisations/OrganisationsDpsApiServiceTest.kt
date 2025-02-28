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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.OrganisationsDpsApiMockServer.Companion.syncCreateAddressRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.OrganisationsDpsApiMockServer.Companion.syncCreateEmailRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.OrganisationsDpsApiMockServer.Companion.syncCreateOrganisationAddressPhoneRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.OrganisationsDpsApiMockServer.Companion.syncCreateOrganisationRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.OrganisationsDpsApiMockServer.Companion.syncCreatePhoneRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.OrganisationsDpsApiMockServer.Companion.syncCreateWebRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.OrganisationsDpsApiMockServer.Companion.syncUpdateAddressPhoneRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.OrganisationsDpsApiMockServer.Companion.syncUpdateAddressRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.OrganisationsDpsApiMockServer.Companion.syncUpdateEmailRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.OrganisationsDpsApiMockServer.Companion.syncUpdateOrganisationRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.OrganisationsDpsApiMockServer.Companion.syncUpdatePhoneRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.OrganisationsDpsApiMockServer.Companion.syncUpdateTypesRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.OrganisationsDpsApiMockServer.Companion.syncUpdateWebRequest

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

      apiService.createOrganisationAddress(syncCreateAddressRequest())

      dpsOrganisationsServer.verify(
        postRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the POST endpoint`() = runTest {
      dpsOrganisationsServer.stubCreateOrganisationAddress()

      apiService.createOrganisationAddress(syncCreateAddressRequest())

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

      apiService.updateOrganisationAddress(12345, syncUpdateAddressRequest())

      dpsOrganisationsServer.verify(
        putRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the PUT endpoint`() = runTest {
      dpsOrganisationsServer.stubUpdateOrganisationAddress(12345)

      apiService.updateOrganisationAddress(12345, syncUpdateAddressRequest())

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

  @Nested
  inner class CreateOrganisationPhone {
    @Test
    internal fun `will pass oath2 token to organisation phone endpoint`() = runTest {
      dpsOrganisationsServer.stubCreateOrganisationPhone()

      apiService.createOrganisationPhone(syncCreatePhoneRequest())

      dpsOrganisationsServer.verify(
        postRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the POST endpoint`() = runTest {
      dpsOrganisationsServer.stubCreateOrganisationPhone()

      apiService.createOrganisationPhone(syncCreatePhoneRequest())

      dpsOrganisationsServer.verify(
        postRequestedFor(urlPathEqualTo("/sync/organisation-phone")),
      )
    }
  }

  @Nested
  inner class UpdateOrganisationPhone {
    @Test
    internal fun `will pass oath2 token to organisation-phone endpoint`() = runTest {
      dpsOrganisationsServer.stubUpdateOrganisationPhone(12345)

      apiService.updateOrganisationPhone(12345, syncUpdatePhoneRequest())

      dpsOrganisationsServer.verify(
        putRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the PUT endpoint`() = runTest {
      dpsOrganisationsServer.stubUpdateOrganisationPhone(12345)

      apiService.updateOrganisationPhone(12345, syncUpdatePhoneRequest())

      dpsOrganisationsServer.verify(
        putRequestedFor(urlPathEqualTo("/sync/organisation-phone/12345")),
      )
    }
  }

  @Nested
  inner class DeleteOrganisationPhone {
    @Test
    internal fun `will pass oath2 token to organisation phone endpoint`() = runTest {
      dpsOrganisationsServer.stubDeleteOrganisationPhone(12345)

      apiService.deleteOrganisationPhone(12345)

      dpsOrganisationsServer.verify(
        deleteRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the DELETE endpoint`() = runTest {
      dpsOrganisationsServer.stubDeleteOrganisationPhone(12345)

      apiService.deleteOrganisationPhone(12345)

      dpsOrganisationsServer.verify(
        deleteRequestedFor(urlPathEqualTo("/sync/organisation-phone/12345")),
      )
    }
  }

  @Nested
  inner class CreateOrganisationAddressPhone {
    @Test
    internal fun `will pass oath2 token to organisation address phone endpoint`() = runTest {
      dpsOrganisationsServer.stubCreateOrganisationAddressPhone()

      apiService.createOrganisationAddressPhone(syncCreateOrganisationAddressPhoneRequest())

      dpsOrganisationsServer.verify(
        postRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the POST endpoint`() = runTest {
      dpsOrganisationsServer.stubCreateOrganisationAddressPhone()

      apiService.createOrganisationAddressPhone(syncCreateOrganisationAddressPhoneRequest())

      dpsOrganisationsServer.verify(
        postRequestedFor(urlPathEqualTo("/sync/organisation-address-phone")),
      )
    }
  }

  @Nested
  inner class UpdateOrganisationAddressPhone {
    @Test
    internal fun `will pass oath2 token to organisation address phone endpoint`() = runTest {
      dpsOrganisationsServer.stubUpdateOrganisationAddressPhone(12345)

      apiService.updateOrganisationAddressPhone(12345, syncUpdateAddressPhoneRequest())

      dpsOrganisationsServer.verify(
        putRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the PUT endpoint`() = runTest {
      dpsOrganisationsServer.stubUpdateOrganisationAddressPhone(12345)

      apiService.updateOrganisationAddressPhone(12345, syncUpdateAddressPhoneRequest())

      dpsOrganisationsServer.verify(
        putRequestedFor(urlPathEqualTo("/sync/organisation-address-phone/12345")),
      )
    }
  }

  @Nested
  inner class DeleteOrganisationAddressPhone {
    @Test
    internal fun `will pass oath2 token to organisation address phone endpoint`() = runTest {
      dpsOrganisationsServer.stubDeleteOrganisationAddressPhone(12345)

      apiService.deleteOrganisationAddressPhone(12345)

      dpsOrganisationsServer.verify(
        deleteRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the DELETE endpoint`() = runTest {
      dpsOrganisationsServer.stubDeleteOrganisationAddressPhone(12345)

      apiService.deleteOrganisationAddressPhone(12345)

      dpsOrganisationsServer.verify(
        deleteRequestedFor(urlPathEqualTo("/sync/organisation-address-phone/12345")),
      )
    }
  }

  @Nested
  inner class CreateOrganisationWebAddress {
    @Test
    internal fun `will pass oath2 token to organisation web address endpoint`() = runTest {
      dpsOrganisationsServer.stubCreateOrganisationWebAddress()

      apiService.createOrganisationWebAddress(syncCreateWebRequest())

      dpsOrganisationsServer.verify(
        postRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the POST endpoint`() = runTest {
      dpsOrganisationsServer.stubCreateOrganisationWebAddress()

      apiService.createOrganisationWebAddress(syncCreateWebRequest())

      dpsOrganisationsServer.verify(
        postRequestedFor(urlPathEqualTo("/sync/organisation-web")),
      )
    }
  }

  @Nested
  inner class UpdateOrganisationWebAddress {
    @Test
    internal fun `will pass oath2 token to organisation web address endpoint`() = runTest {
      dpsOrganisationsServer.stubUpdateOrganisationWebAddress(12345)

      apiService.updateOrganisationWebAddress(12345, syncUpdateWebRequest())

      dpsOrganisationsServer.verify(
        putRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the PUT endpoint`() = runTest {
      dpsOrganisationsServer.stubUpdateOrganisationWebAddress(12345)

      apiService.updateOrganisationWebAddress(12345, syncUpdateWebRequest())

      dpsOrganisationsServer.verify(
        putRequestedFor(urlPathEqualTo("/sync/organisation-web/12345")),
      )
    }
  }

  @Nested
  inner class DeleteOrganisationWebAddress {
    @Test
    internal fun `will pass oath2 token to organisation web address endpoint`() = runTest {
      dpsOrganisationsServer.stubDeleteOrganisationWebAddress(12345)

      apiService.deleteOrganisationWebAddress(12345)

      dpsOrganisationsServer.verify(
        deleteRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the DELETE endpoint`() = runTest {
      dpsOrganisationsServer.stubDeleteOrganisationWebAddress(12345)

      apiService.deleteOrganisationWebAddress(12345)

      dpsOrganisationsServer.verify(
        deleteRequestedFor(urlPathEqualTo("/sync/organisation-web/12345")),
      )
    }
  }

  @Nested
  inner class CreateOrganisationEmail {
    @Test
    internal fun `will pass oath2 token to organisation email endpoint`() = runTest {
      dpsOrganisationsServer.stubCreateOrganisationEmail()

      apiService.createOrganisationEmail(syncCreateEmailRequest())

      dpsOrganisationsServer.verify(
        postRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the POST endpoint`() = runTest {
      dpsOrganisationsServer.stubCreateOrganisationEmail()

      apiService.createOrganisationEmail(syncCreateEmailRequest())

      dpsOrganisationsServer.verify(
        postRequestedFor(urlPathEqualTo("/sync/organisation-email")),
      )
    }
  }

  @Nested
  inner class UpdateOrganisationEmail {
    @Test
    internal fun `will pass oath2 token to organisation-email endpoint`() = runTest {
      dpsOrganisationsServer.stubUpdateOrganisationEmail(12345)

      apiService.updateOrganisationEmail(12345, syncUpdateEmailRequest())

      dpsOrganisationsServer.verify(
        putRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the PUT endpoint`() = runTest {
      dpsOrganisationsServer.stubUpdateOrganisationEmail(12345)

      apiService.updateOrganisationEmail(12345, syncUpdateEmailRequest())

      dpsOrganisationsServer.verify(
        putRequestedFor(urlPathEqualTo("/sync/organisation-email/12345")),
      )
    }
  }

  @Nested
  inner class DeleteOrganisationEmail {
    @Test
    internal fun `will pass oath2 token to organisation email endpoint`() = runTest {
      dpsOrganisationsServer.stubDeleteOrganisationEmail(12345)

      apiService.deleteOrganisationEmail(12345)

      dpsOrganisationsServer.verify(
        deleteRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the DELETE endpoint`() = runTest {
      dpsOrganisationsServer.stubDeleteOrganisationEmail(12345)

      apiService.deleteOrganisationEmail(12345)

      dpsOrganisationsServer.verify(
        deleteRequestedFor(urlPathEqualTo("/sync/organisation-email/12345")),
      )
    }
  }

  @Nested
  inner class UpdateOrganisationTypes {
    @Test
    internal fun `will pass oath2 token to organisation-types endpoint`() = runTest {
      dpsOrganisationsServer.stubUpdateOrganisationTypes(12345)

      apiService.updateOrganisationTypes(12345, syncUpdateTypesRequest())

      dpsOrganisationsServer.verify(
        putRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the PUT endpoint`() = runTest {
      dpsOrganisationsServer.stubUpdateOrganisationTypes(12345)

      apiService.updateOrganisationTypes(12345, syncUpdateTypesRequest())

      dpsOrganisationsServer.verify(
        putRequestedFor(urlPathEqualTo("/sync/organisation-types/12345")),
      )
    }
  }
}
