package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.ContactPersonDpsApiExtension.Companion.dpsContactPersonServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.ContactPersonDpsApiMockServer.Companion.createContactAddressPhoneRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.ContactPersonDpsApiMockServer.Companion.createContactAddressRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.ContactPersonDpsApiMockServer.Companion.createContactEmailRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.ContactPersonDpsApiMockServer.Companion.createContactIdentityRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.ContactPersonDpsApiMockServer.Companion.createContactPhoneRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.ContactPersonDpsApiMockServer.Companion.createContactRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.ContactPersonDpsApiMockServer.Companion.createContactRestrictionRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.ContactPersonDpsApiMockServer.Companion.createPrisonerContactRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.ContactPersonDpsApiMockServer.Companion.createPrisonerContactRestrictionRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.ContactPersonDpsApiMockServer.Companion.migrateContactRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.ContactPersonDpsApiMockServer.Companion.updateContactRestrictionRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.ContactPersonDpsApiMockServer.Companion.updatePrisonerContactRestrictionRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest

@SpringAPIServiceTest
@Import(ContactPersonDpsApiService::class, ContactPersonConfiguration::class)
class ContactPersonDpsApiServiceTest {
  @Autowired
  private lateinit var apiService: ContactPersonDpsApiService

  @Nested
  inner class MigrateContact {
    @Test
    internal fun `will pass oath2 token to contact endpoint`() = runTest {
      dpsContactPersonServer.stubMigrateContact()

      apiService.migrateContact(migrateContactRequest())

      dpsContactPersonServer.verify(
        postRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the migrate endpoint`() = runTest {
      dpsContactPersonServer.stubMigrateContact()

      apiService.migrateContact(migrateContactRequest())

      dpsContactPersonServer.verify(
        postRequestedFor(urlPathEqualTo("/migrate/contact")),
      )
    }
  }

  @Nested
  inner class CreateContact {
    @Test
    internal fun `will pass oath2 token to contact endpoint`() = runTest {
      dpsContactPersonServer.stubCreateContact()

      apiService.createContact(createContactRequest())

      dpsContactPersonServer.verify(
        postRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the create sync endpoint`() = runTest {
      dpsContactPersonServer.stubCreateContact()

      apiService.createContact(createContactRequest())

      dpsContactPersonServer.verify(
        postRequestedFor(urlPathEqualTo("/sync/contact")),
      )
    }
  }

  @Nested
  inner class CreatePrisonerContact {
    @Test
    internal fun `will pass oath2 token to prisoner contact endpoint`() = runTest {
      dpsContactPersonServer.stubCreatePrisonerContact()

      apiService.createPrisonerContact(createPrisonerContactRequest())

      dpsContactPersonServer.verify(
        postRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the create sync endpoint`() = runTest {
      dpsContactPersonServer.stubCreatePrisonerContact()

      apiService.createPrisonerContact(createPrisonerContactRequest())

      dpsContactPersonServer.verify(
        postRequestedFor(urlPathEqualTo("/sync/prisoner-contact")),
      )
    }
  }

  @Nested
  inner class CreateContactAddress {
    @Test
    internal fun `will pass oath2 token to endpoint`() = runTest {
      dpsContactPersonServer.stubCreateContactAddress()

      apiService.createContactAddress(createContactAddressRequest())

      dpsContactPersonServer.verify(
        postRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the create sync endpoint`() = runTest {
      dpsContactPersonServer.stubCreateContactAddress()

      apiService.createContactAddress(createContactAddressRequest())

      dpsContactPersonServer.verify(
        postRequestedFor(urlPathEqualTo("/sync/contact-address")),
      )
    }
  }

  @Nested
  inner class CreateContactEmail {
    @Test
    internal fun `will pass oath2 token to endpoint`() = runTest {
      dpsContactPersonServer.stubCreateContactEmail()

      apiService.createContactEmail(createContactEmailRequest())

      dpsContactPersonServer.verify(
        postRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the create sync endpoint`() = runTest {
      dpsContactPersonServer.stubCreateContactEmail()

      apiService.createContactEmail(createContactEmailRequest())

      dpsContactPersonServer.verify(
        postRequestedFor(urlPathEqualTo("/sync/contact-email")),
      )
    }
  }

  @Nested
  inner class CreateContactPhone {
    @Test
    internal fun `will pass oath2 token to endpoint`() = runTest {
      dpsContactPersonServer.stubCreateContactPhone()

      apiService.createContactPhone(createContactPhoneRequest())

      dpsContactPersonServer.verify(
        postRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the create sync endpoint`() = runTest {
      dpsContactPersonServer.stubCreateContactPhone()

      apiService.createContactPhone(createContactPhoneRequest())

      dpsContactPersonServer.verify(
        postRequestedFor(urlPathEqualTo("/sync/contact-phone")),
      )
    }
  }

  @Nested
  inner class CreateContactAddressPhone {
    @Test
    internal fun `will pass oath2 token to endpoint`() = runTest {
      dpsContactPersonServer.stubCreateContactAddressPhone()

      apiService.createContactAddressPhone(createContactAddressPhoneRequest())

      dpsContactPersonServer.verify(
        postRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the create sync endpoint`() = runTest {
      dpsContactPersonServer.stubCreateContactAddressPhone()

      apiService.createContactAddressPhone(createContactAddressPhoneRequest())

      dpsContactPersonServer.verify(
        postRequestedFor(urlPathEqualTo("/sync/contact-address-phone")),
      )
    }
  }

  @Nested
  inner class CreateContactIdentity {
    @Test
    internal fun `will pass oath2 token to endpoint`() = runTest {
      dpsContactPersonServer.stubCreateContactIdentity()

      apiService.createContactIdentity(createContactIdentityRequest())

      dpsContactPersonServer.verify(
        postRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the create sync endpoint`() = runTest {
      dpsContactPersonServer.stubCreateContactIdentity()

      apiService.createContactIdentity(createContactIdentityRequest())

      dpsContactPersonServer.verify(
        postRequestedFor(urlPathEqualTo("/sync/contact-identity")),
      )
    }
  }

  @Nested
  inner class CreatePrisonerContactRestriction {
    @Test
    internal fun `will pass oath2 token to prisoner contact endpoint`() = runTest {
      dpsContactPersonServer.stubCreatePrisonerContactRestriction()

      apiService.createPrisonerContactRestriction(createPrisonerContactRestrictionRequest())

      dpsContactPersonServer.verify(
        postRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the create sync endpoint`() = runTest {
      dpsContactPersonServer.stubCreatePrisonerContactRestriction()

      apiService.createPrisonerContactRestriction(createPrisonerContactRestrictionRequest())

      dpsContactPersonServer.verify(
        postRequestedFor(urlPathEqualTo("/sync/prisoner-contact-restriction")),
      )
    }
  }

  @Nested
  inner class UpdatePrisonerContactRestriction {
    private val prisonerContactRestrictionId = 12345L

    @Test
    internal fun `will pass oath2 token to prisoner contact restriction endpoint`() = runTest {
      dpsContactPersonServer.stubUpdatePrisonerContactRestriction(prisonerContactRestrictionId)

      apiService.updatePrisonerContactRestriction(prisonerContactRestrictionId, updatePrisonerContactRestrictionRequest())

      dpsContactPersonServer.verify(
        putRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the update sync endpoint`() = runTest {
      dpsContactPersonServer.stubUpdatePrisonerContactRestriction(prisonerContactRestrictionId)

      apiService.updatePrisonerContactRestriction(prisonerContactRestrictionId, updatePrisonerContactRestrictionRequest())

      dpsContactPersonServer.verify(
        putRequestedFor(urlPathEqualTo("/sync/prisoner-contact-restriction/$prisonerContactRestrictionId")),
      )
    }
  }

  @Nested
  inner class CreateContactRestriction {
    @Test
    internal fun `will pass oath2 token to  contact endpoint`() = runTest {
      dpsContactPersonServer.stubCreateContactRestriction()

      apiService.createContactRestriction(createContactRestrictionRequest())

      dpsContactPersonServer.verify(
        postRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the create sync endpoint`() = runTest {
      dpsContactPersonServer.stubCreateContactRestriction()

      apiService.createContactRestriction(createContactRestrictionRequest())

      dpsContactPersonServer.verify(
        postRequestedFor(urlPathEqualTo("/sync/contact-restriction")),
      )
    }
  }

  @Nested
  inner class UpdateContactRestriction {
    private val contactRestrictionId = 1234L

    @Test
    internal fun `will pass oath2 token to contact restriction endpoint`() = runTest {
      dpsContactPersonServer.stubUpdateContactRestriction(contactRestrictionId)

      apiService.updateContactRestriction(contactRestrictionId, updateContactRestrictionRequest())

      dpsContactPersonServer.verify(
        putRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the update sync endpoint`() = runTest {
      dpsContactPersonServer.stubUpdateContactRestriction(contactRestrictionId)

      apiService.updateContactRestriction(contactRestrictionId, updateContactRestrictionRequest())

      dpsContactPersonServer.verify(
        putRequestedFor(urlPathEqualTo("/sync/contact-restriction/$contactRestrictionId")),
      )
    }
  }
}
