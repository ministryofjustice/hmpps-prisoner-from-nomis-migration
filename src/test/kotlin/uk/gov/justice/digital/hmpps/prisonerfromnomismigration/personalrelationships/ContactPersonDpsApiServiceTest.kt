package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.ContactPersonDpsApiExtension.Companion.dpsContactPersonServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.ContactPersonDpsApiMockServer.Companion.createContactAddressPhoneRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.ContactPersonDpsApiMockServer.Companion.createContactAddressRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.ContactPersonDpsApiMockServer.Companion.createContactEmailRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.ContactPersonDpsApiMockServer.Companion.createContactEmploymentRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.ContactPersonDpsApiMockServer.Companion.createContactIdentityRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.ContactPersonDpsApiMockServer.Companion.createContactPhoneRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.ContactPersonDpsApiMockServer.Companion.createContactRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.ContactPersonDpsApiMockServer.Companion.createContactRestrictionRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.ContactPersonDpsApiMockServer.Companion.createPrisonerContactRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.ContactPersonDpsApiMockServer.Companion.createPrisonerContactRestrictionRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.ContactPersonDpsApiMockServer.Companion.migrateContactRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.ContactPersonDpsApiMockServer.Companion.updateContactAddressPhoneRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.ContactPersonDpsApiMockServer.Companion.updateContactAddressRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.ContactPersonDpsApiMockServer.Companion.updateContactEmailRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.ContactPersonDpsApiMockServer.Companion.updateContactEmploymentRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.ContactPersonDpsApiMockServer.Companion.updateContactIdentityRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.ContactPersonDpsApiMockServer.Companion.updateContactPhoneRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.ContactPersonDpsApiMockServer.Companion.updateContactRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.ContactPersonDpsApiMockServer.Companion.updateContactRestrictionRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.ContactPersonDpsApiMockServer.Companion.updatePrisonerContactRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.ContactPersonDpsApiMockServer.Companion.updatePrisonerContactRestrictionRequest

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
  inner class UpdateContact {
    private val contactId = 12345L

    @Test
    internal fun `will pass oath2 token to contact endpoint`() = runTest {
      dpsContactPersonServer.stubUpdateContact(contactId)

      apiService.updateContact(contactId, updateContactRequest())

      dpsContactPersonServer.verify(
        putRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the update sync endpoint`() = runTest {
      dpsContactPersonServer.stubUpdateContact(contactId)

      apiService.updateContact(contactId, updateContactRequest())

      dpsContactPersonServer.verify(
        putRequestedFor(urlPathEqualTo("/sync/contact/$contactId")),
      )
    }
  }

  @Nested
  inner class DeleteContact {
    private val contactId = 12345L

    @Test
    internal fun `will pass oath2 token to contact endpoint`() = runTest {
      dpsContactPersonServer.stubDeleteContact(contactId)

      apiService.deleteContact(contactId)

      dpsContactPersonServer.verify(
        deleteRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the delete sync endpoint`() = runTest {
      dpsContactPersonServer.stubDeleteContact(contactId)

      apiService.deleteContact(contactId)

      dpsContactPersonServer.verify(
        deleteRequestedFor(urlPathEqualTo("/sync/contact/$contactId")),
      )
    }

    @Test
    fun `will not throw exception when there is a  404`() = runTest {
      dpsContactPersonServer.stubDeleteContact(contactId, status = 404)

      assertDoesNotThrow { apiService.deleteContact(contactId) }
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
  inner class UpdatePrisonerContact {
    private val prisonerContactId = 12345L

    @Test
    internal fun `will pass oath2 token to contact endpoint`() = runTest {
      dpsContactPersonServer.stubUpdatePrisonerContact(prisonerContactId)

      apiService.updatePrisonerContact(prisonerContactId, updatePrisonerContactRequest())

      dpsContactPersonServer.verify(
        putRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the update sync endpoint`() = runTest {
      dpsContactPersonServer.stubUpdatePrisonerContact(prisonerContactId)

      apiService.updatePrisonerContact(prisonerContactId, updatePrisonerContactRequest())

      dpsContactPersonServer.verify(
        putRequestedFor(urlPathEqualTo("/sync/prisoner-contact/$prisonerContactId")),
      )
    }
  }

  @Nested
  inner class DeletePrisonerContact {
    private val prisonerContactId = 12345L

    @Test
    internal fun `will pass oath2 token to contact endpoint`() = runTest {
      dpsContactPersonServer.stubDeletePrisonerContact(prisonerContactId)

      apiService.deletePrisonerContact(prisonerContactId)

      dpsContactPersonServer.verify(
        deleteRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the delete sync endpoint`() = runTest {
      dpsContactPersonServer.stubDeletePrisonerContact(prisonerContactId)

      apiService.deletePrisonerContact(prisonerContactId)

      dpsContactPersonServer.verify(
        deleteRequestedFor(urlPathEqualTo("/sync/prisoner-contact/$prisonerContactId")),
      )
    }

    @Test
    fun `will not throw exception when there is a 404`() = runTest {
      dpsContactPersonServer.stubDeletePrisonerContact(prisonerContactId, status = 404)

      assertDoesNotThrow { apiService.deletePrisonerContact(prisonerContactId) }
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
  inner class UpdateContactAddress {
    val addressId = 27272L

    @Test
    internal fun `will pass oath2 token to endpoint`() = runTest {
      dpsContactPersonServer.stubUpdateContactAddress(addressId)

      apiService.updateContactAddress(addressId, updateContactAddressRequest())

      dpsContactPersonServer.verify(
        putRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the update sync endpoint`() = runTest {
      dpsContactPersonServer.stubUpdateContactAddress(addressId)

      apiService.updateContactAddress(addressId, updateContactAddressRequest())

      dpsContactPersonServer.verify(
        putRequestedFor(urlPathEqualTo("/sync/contact-address/$addressId")),
      )
    }
  }

  @Nested
  inner class DeleteContactAddress {
    private val contactAddressId = 12345L

    @Test
    internal fun `will pass oath2 token to endpoint`() = runTest {
      dpsContactPersonServer.stubDeleteContactAddress(contactAddressId)

      apiService.deleteContactAddress(contactAddressId)

      dpsContactPersonServer.verify(
        deleteRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the delete sync endpoint`() = runTest {
      dpsContactPersonServer.stubDeleteContactAddress(contactAddressId)

      apiService.deleteContactAddress(contactAddressId)

      dpsContactPersonServer.verify(
        deleteRequestedFor(urlPathEqualTo("/sync/contact-address/$contactAddressId")),
      )
    }

    @Test
    fun `will ignore 404`() = runTest {
      dpsContactPersonServer.stubDeleteContactAddress(contactAddressId, status = 404)

      apiService.deleteContactAddress(contactAddressId)

      dpsContactPersonServer.verify(
        deleteRequestedFor(urlPathEqualTo("/sync/contact-address/$contactAddressId")),
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
  inner class UpdateContactEmail {
    @Test
    internal fun `will pass oath2 token to endpoint`() = runTest {
      dpsContactPersonServer.stubUpdateContactEmail(contactEmailId = 123456)

      apiService.updateContactEmail(contactEmailId = 123456, updateContactEmailRequest())

      dpsContactPersonServer.verify(
        putRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the update sync endpoint`() = runTest {
      dpsContactPersonServer.stubUpdateContactEmail(contactEmailId = 123456)

      apiService.updateContactEmail(contactEmailId = 123456, updateContactEmailRequest())

      dpsContactPersonServer.verify(
        putRequestedFor(urlPathEqualTo("/sync/contact-email/123456")),
      )
    }
  }

  @Nested
  inner class DeleteContactEmail {
    private val contactEmailId = 12345L

    @Test
    internal fun `will pass oath2 token to endpoint`() = runTest {
      dpsContactPersonServer.stubDeleteContactEmail(contactEmailId)

      apiService.deleteContactEmail(contactEmailId)

      dpsContactPersonServer.verify(
        deleteRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the delete sync endpoint`() = runTest {
      dpsContactPersonServer.stubDeleteContactEmail(contactEmailId)

      apiService.deleteContactEmail(contactEmailId)

      dpsContactPersonServer.verify(
        deleteRequestedFor(urlPathEqualTo("/sync/contact-email/$contactEmailId")),
      )
    }

    @Test
    fun `will ignore 404`() = runTest {
      dpsContactPersonServer.stubDeleteContactEmail(contactEmailId, status = 404)

      apiService.deleteContactEmail(contactEmailId)

      dpsContactPersonServer.verify(
        deleteRequestedFor(urlPathEqualTo("/sync/contact-email/$contactEmailId")),
      )
    }
  }

  @Nested
  inner class CreateContactEmployment {
    @Test
    internal fun `will pass oath2 token to endpoint`() = runTest {
      dpsContactPersonServer.stubCreateContactEmployment()

      apiService.createContactEmployment(createContactEmploymentRequest())

      dpsContactPersonServer.verify(
        postRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the create sync endpoint`() = runTest {
      dpsContactPersonServer.stubCreateContactEmployment()

      apiService.createContactEmployment(createContactEmploymentRequest())

      dpsContactPersonServer.verify(
        postRequestedFor(urlPathEqualTo("/sync/employment")),
      )
    }
  }

  @Nested
  inner class UpdateContactEmployment {
    @Test
    internal fun `will pass oath2 token to endpoint`() = runTest {
      dpsContactPersonServer.stubUpdateContactEmployment(contactEmploymentId = 123456)

      apiService.updateContactEmployment(contactEmploymentId = 123456, updateContactEmploymentRequest())

      dpsContactPersonServer.verify(
        putRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the update sync endpoint`() = runTest {
      dpsContactPersonServer.stubUpdateContactEmployment(contactEmploymentId = 123456)

      apiService.updateContactEmployment(contactEmploymentId = 123456, updateContactEmploymentRequest())

      dpsContactPersonServer.verify(
        putRequestedFor(urlPathEqualTo("/sync/employment/123456")),
      )
    }
  }

  @Nested
  inner class DeleteContactEmployment {
    private val contactEmploymentId = 12345L

    @Test
    internal fun `will pass oath2 token to endpoint`() = runTest {
      dpsContactPersonServer.stubDeleteContactEmployment(contactEmploymentId)

      apiService.deleteContactEmployment(contactEmploymentId)

      dpsContactPersonServer.verify(
        deleteRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the delete sync endpoint`() = runTest {
      dpsContactPersonServer.stubDeleteContactEmployment(contactEmploymentId)

      apiService.deleteContactEmployment(contactEmploymentId)

      dpsContactPersonServer.verify(
        deleteRequestedFor(urlPathEqualTo("/sync/employment/$contactEmploymentId")),
      )
    }

    @Test
    fun `will ignore 404`() = runTest {
      dpsContactPersonServer.stubDeleteContactEmployment(contactEmploymentId, status = 404)

      apiService.deleteContactEmployment(contactEmploymentId)

      dpsContactPersonServer.verify(
        deleteRequestedFor(urlPathEqualTo("/sync/employment/$contactEmploymentId")),
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
  inner class UpdateContactPhone {
    val contactPhoneId = 12345L

    @Test
    internal fun `will pass oath2 token to endpoint`() = runTest {
      dpsContactPersonServer.stubUpdateContactPhone(contactPhoneId)

      apiService.updateContactPhone(contactPhoneId, updateContactPhoneRequest())

      dpsContactPersonServer.verify(
        putRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the update sync endpoint`() = runTest {
      dpsContactPersonServer.stubUpdateContactPhone(contactPhoneId)

      apiService.updateContactPhone(contactPhoneId, updateContactPhoneRequest())

      dpsContactPersonServer.verify(
        putRequestedFor(urlPathEqualTo("/sync/contact-phone/$contactPhoneId")),
      )
    }
  }

  @Nested
  inner class DeleteContactPhone {
    val contactPhoneId = 12345L

    @Test
    internal fun `will pass oath2 token to endpoint`() = runTest {
      dpsContactPersonServer.stubDeleteContactPhone(contactPhoneId)

      apiService.deleteContactPhone(contactPhoneId)

      dpsContactPersonServer.verify(
        deleteRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the delete sync endpoint`() = runTest {
      dpsContactPersonServer.stubDeleteContactPhone(contactPhoneId)

      apiService.deleteContactPhone(contactPhoneId)

      dpsContactPersonServer.verify(
        deleteRequestedFor(urlPathEqualTo("/sync/contact-phone/$contactPhoneId")),
      )
    }

    @Test
    fun `will not throw exception when there is a 404`() = runTest {
      dpsContactPersonServer.stubDeleteContactPhone(contactPhoneId, 404)

      assertDoesNotThrow { apiService.deleteContactPhone(contactPhoneId) }
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
  inner class UpdateContactAddressPhone {
    val contactAddressPhoneId = 12345L

    @Test
    internal fun `will pass oath2 token to endpoint`() = runTest {
      dpsContactPersonServer.stubUpdateContactAddressPhone(contactAddressPhoneId)

      apiService.updateContactAddressPhone(contactAddressPhoneId, updateContactAddressPhoneRequest())

      dpsContactPersonServer.verify(
        putRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the update sync endpoint`() = runTest {
      dpsContactPersonServer.stubUpdateContactAddressPhone(contactAddressPhoneId)

      apiService.updateContactAddressPhone(contactAddressPhoneId, updateContactAddressPhoneRequest())

      dpsContactPersonServer.verify(
        putRequestedFor(urlPathEqualTo("/sync/contact-address-phone/$contactAddressPhoneId")),
      )
    }
  }

  @Nested
  inner class DeleteContactAddressPhone {
    val contactAddressPhoneId = 12345L

    @Test
    internal fun `will pass oath2 token to endpoint`() = runTest {
      dpsContactPersonServer.stubDeleteContactAddressPhone(contactAddressPhoneId)

      apiService.deleteContactAddressPhone(contactAddressPhoneId)

      dpsContactPersonServer.verify(
        deleteRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the delete sync endpoint`() = runTest {
      dpsContactPersonServer.stubDeleteContactAddressPhone(contactAddressPhoneId)

      apiService.deleteContactAddressPhone(contactAddressPhoneId)

      dpsContactPersonServer.verify(
        deleteRequestedFor(urlPathEqualTo("/sync/contact-address-phone/$contactAddressPhoneId")),
      )
    }

    @Test
    fun `will ignore 404`() = runTest {
      dpsContactPersonServer.stubDeleteContactAddressPhone(contactAddressPhoneId, status = 404)

      apiService.deleteContactAddressPhone(contactAddressPhoneId)

      dpsContactPersonServer.verify(
        deleteRequestedFor(urlPathEqualTo("/sync/contact-address-phone/$contactAddressPhoneId")),
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
  inner class UpdateContactIdentity {
    @Test
    internal fun `will pass oath2 token to endpoint`() = runTest {
      dpsContactPersonServer.stubUpdateContactIdentity(contactIdentityId = 123456)

      apiService.updateContactIdentity(contactIdentityId = 123456, updateContactIdentityRequest())

      dpsContactPersonServer.verify(
        putRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the update sync endpoint`() = runTest {
      dpsContactPersonServer.stubUpdateContactIdentity(contactIdentityId = 123456)

      apiService.updateContactIdentity(contactIdentityId = 123456, updateContactIdentityRequest())

      dpsContactPersonServer.verify(
        putRequestedFor(urlPathEqualTo("/sync/contact-identity/123456")),
      )
    }
  }

  @Nested
  inner class DeleteContactIdentity {
    private val contactIdentityId = 12345L

    @Test
    internal fun `will pass oath2 token to endpoint`() = runTest {
      dpsContactPersonServer.stubDeleteContactIdentity(contactIdentityId)

      apiService.deleteContactIdentity(contactIdentityId)

      dpsContactPersonServer.verify(
        deleteRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the delete sync endpoint`() = runTest {
      dpsContactPersonServer.stubDeleteContactIdentity(contactIdentityId)

      apiService.deleteContactIdentity(contactIdentityId)

      dpsContactPersonServer.verify(
        deleteRequestedFor(urlPathEqualTo("/sync/contact-identity/$contactIdentityId")),
      )
    }

    @Test
    fun `will ignore 404`() = runTest {
      dpsContactPersonServer.stubDeleteContactIdentity(contactIdentityId, status = 404)

      apiService.deleteContactIdentity(contactIdentityId)

      dpsContactPersonServer.verify(
        deleteRequestedFor(urlPathEqualTo("/sync/contact-identity/$contactIdentityId")),
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
  inner class DeletePrisonerContactRestriction {
    private val prisonerContactRestrictionId = 12345L

    @Test
    internal fun `will pass oath2 token to prisoner contact restriction endpoint`() = runTest {
      dpsContactPersonServer.stubDeletePrisonerContactRestriction(prisonerContactRestrictionId)

      apiService.deletePrisonerContactRestriction(prisonerContactRestrictionId)

      dpsContactPersonServer.verify(
        deleteRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the delete sync endpoint`() = runTest {
      dpsContactPersonServer.stubDeletePrisonerContactRestriction(prisonerContactRestrictionId)

      apiService.deletePrisonerContactRestriction(prisonerContactRestrictionId)

      dpsContactPersonServer.verify(
        deleteRequestedFor(urlPathEqualTo("/sync/prisoner-contact-restriction/$prisonerContactRestrictionId")),
      )
    }

    @Test
    fun `will not throw exception when there is a  404`() = runTest {
      dpsContactPersonServer.stubDeletePrisonerContactRestriction(prisonerContactRestrictionId, status = 404)

      assertDoesNotThrow { apiService.deletePrisonerContactRestriction(prisonerContactRestrictionId) }
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

  @Nested
  inner class DeleteContactRestriction {
    private val contactRestrictionId = 1234L

    @Test
    internal fun `will pass oath2 token to contact restriction endpoint`() = runTest {
      dpsContactPersonServer.stubDeleteContactRestriction(contactRestrictionId)

      apiService.deleteContactRestriction(contactRestrictionId)

      dpsContactPersonServer.verify(
        deleteRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the delete sync endpoint`() = runTest {
      dpsContactPersonServer.stubDeleteContactRestriction(contactRestrictionId)

      apiService.deleteContactRestriction(contactRestrictionId)

      dpsContactPersonServer.verify(
        deleteRequestedFor(urlPathEqualTo("/sync/contact-restriction/$contactRestrictionId")),
      )
    }

    @Test
    fun `will not throw exception when there is a  404`() = runTest {
      dpsContactPersonServer.stubDeleteContactRestriction(contactRestrictionId, status = 404)

      assertDoesNotThrow { apiService.deleteContactRestriction(contactRestrictionId) }
    }
  }
}
