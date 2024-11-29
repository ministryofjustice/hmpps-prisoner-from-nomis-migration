package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.test.context.junit.jupiter.SpringExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.AddressAndPhones
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.ContactsAndRestrictions
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.IdPair
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.MigrateContactRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.MigrateContactResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.SyncContact
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.SyncContactAddress
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.SyncContactAddressPhone
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.SyncContactEmail
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.SyncContactPhone
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.SyncCreateContactAddressPhoneRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.SyncCreateContactAddressRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.SyncCreateContactEmailRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.SyncCreateContactPhoneRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.SyncCreateContactRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.SyncCreatePrisonerContactRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.SyncPrisonerContact
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.getRequestBodies
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.getRequestBody
import java.time.LocalDateTime

class ContactPersonDpsApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val dpsContactPersonServer = ContactPersonDpsApiMockServer()
    lateinit var objectMapper: ObjectMapper

    @Suppress("unused")
    inline fun <reified T> getRequestBody(pattern: RequestPatternBuilder): T = dpsContactPersonServer.getRequestBody(pattern, objectMapper)
    inline fun <reified T> getRequestBodies(pattern: RequestPatternBuilder): List<T> = dpsContactPersonServer.getRequestBodies(pattern, objectMapper)
  }

  override fun beforeAll(context: ExtensionContext) {
    dpsContactPersonServer.start()
    objectMapper = (SpringExtension.getApplicationContext(context).getBean("jacksonObjectMapper") as ObjectMapper)
  }

  override fun beforeEach(context: ExtensionContext) {
    dpsContactPersonServer.resetAll()
  }

  override fun afterAll(context: ExtensionContext) {
    dpsContactPersonServer.stop()
  }
}

class ContactPersonDpsApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8097

    fun migrateContactRequest() = MigrateContactRequest(
      personId = 654321,
      lastName = "KOFI",
      firstName = "KWEKU",
      staff = false,
      interpreterRequired = false,
      remitter = false,
      phoneNumbers = emptyList(),
      addresses = emptyList(),
      emailAddresses = emptyList(),
      identifiers = emptyList(),
      employments = emptyList(),
      restrictions = emptyList(),
      contacts = emptyList(),
    )

    fun migrateContactResponse(request: MigrateContactRequest = migrateContactRequest()) = MigrateContactResponse(
      contact = IdPair(elementType = IdPair.ElementType.CONTACT, nomisId = request.personId, dpsId = request.personId * 10),
      lastName = request.lastName,
      dateOfBirth = request.dateOfBirth,
      phoneNumbers = request.phoneNumbers.map { IdPair(elementType = IdPair.ElementType.PHONE, nomisId = it.phoneId, dpsId = it.phoneId * 10) },
      addresses = request.addresses.map { AddressAndPhones(address = IdPair(elementType = IdPair.ElementType.ADDRESS, nomisId = it.addressId, dpsId = it.addressId * 10), phones = it.phoneNumbers.map { phone -> IdPair(elementType = IdPair.ElementType.PHONE, nomisId = phone.phoneId, dpsId = phone.phoneId * 10) }) },
      emailAddresses = request.emailAddresses.map { IdPair(elementType = IdPair.ElementType.EMAIL, nomisId = it.emailAddressId, dpsId = it.emailAddressId * 10) },
      identities = request.identifiers.map { IdPair(elementType = IdPair.ElementType.IDENTITY, nomisId = it.sequence, dpsId = it.sequence * 10) },
      employments = request.employments.map { IdPair(elementType = IdPair.ElementType.EMPLOYMENT, nomisId = it.sequence, dpsId = it.sequence * 10) },
      restrictions = request.restrictions.map { IdPair(elementType = IdPair.ElementType.RESTRICTION, nomisId = it.id, dpsId = it.id * 10) },
      relationships = request.contacts.map { ContactsAndRestrictions(relationship = IdPair(elementType = IdPair.ElementType.RESTRICTION, nomisId = it.id, dpsId = it.id * 10), restrictions = it.restrictions.map { restriction -> IdPair(elementType = IdPair.ElementType.RESTRICTION, nomisId = restriction.id, dpsId = restriction.id * 10) }) },
    )

    fun createContactRequest() = SyncCreateContactRequest(
      personId = 654321,
      lastName = "KOFI",
      firstName = "KWEKU",
      isStaff = false,
      interpreterRequired = false,
      remitter = false,
      createdBy = "JANE.SAM",
      createdTime = LocalDateTime.parse("2024-01-01T12:13"),
    )

    fun contact() = SyncContact(
      id = 12345,
      lastName = "KOFI",
      firstName = "KWEKU",
      isStaff = false,
      interpreterRequired = false,
      remitter = false,
      createdBy = "JANE.SAM",
      createdTime = LocalDateTime.parse("2024-01-01T12:13"),
    )

    fun createPrisonerContactRequest() = SyncCreatePrisonerContactRequest(
      contactId = 654321,
      prisonerNumber = "A1234KT",
      contactType = "S",
      relationshipType = "BRO",
      emergencyContact = false,
      nextOfKin = false,
      approvedVisitor = false,
      createdBy = "J.SMITH",
      createdTime = LocalDateTime.parse("2024-01-01T12:13"),
    )
    fun prisonerContact() = SyncPrisonerContact(
      id = 12345,
      contactId = 1234567,
      prisonerNumber = "A1234KT",
      contactType = "S",
      relationshipType = "BRO",
      nextOfKin = true,
      emergencyContact = true,
      approvedVisitor = false,
      currentTerm = true,
      active = true,
      createdBy = "JANE.SAM",
      createdTime = LocalDateTime.parse("2024-01-01T12:13"),
    )
    fun createContactAddressRequest() = SyncCreateContactAddressRequest(
      contactId = 1234567,
      addressType = "MOB",
      primaryAddress = true,
      createdBy = "JANE.SAM",
      createdTime = LocalDateTime.parse("2024-01-01T12:13"),
    )

    fun contactAddress() = SyncContactAddress(
      contactAddressId = 1234567,
      contactId = 12345,
      primaryAddress = true,
      verified = false,
      mailFlag = false,
      noFixedAddress = false,
      createdBy = "JANE.SAM",
      createdTime = LocalDateTime.parse("2024-01-01T12:13"),
    )

    fun createContactEmailRequest() = SyncCreateContactEmailRequest(
      contactId = 1234567,
      emailAddress = "test.test@test.com",
      createdBy = "JANE.SAM",
      createdTime = LocalDateTime.parse("2024-01-01T12:13"),
    )

    fun contactEmail() = SyncContactEmail(
      contactEmailId = 1234567,
      emailAddress = "test.test@test.com",
      contactId = 12345,
      createdBy = "JANE.SAM",
      createdTime = LocalDateTime.parse("2024-01-01T12:13"),
    )

    fun createContactPhoneRequest() = SyncCreateContactPhoneRequest(
      contactId = 1234567,
      phoneType = "MOB",
      phoneNumber = "07973 555 5555",
      createdBy = "JANE.SAM",
      createdTime = LocalDateTime.parse("2024-01-01T12:13"),
    )

    fun contactPhone() = SyncContactPhone(
      contactPhoneId = 1234567,
      phoneType = "MOB",
      phoneNumber = "07973 555 5555",
      contactId = 12345,
      createdBy = "JANE.SAM",
      createdTime = LocalDateTime.parse("2024-01-01T12:13"),
    )
    fun createContactAddressPhoneRequest() = SyncCreateContactAddressPhoneRequest(
      contactAddressId = 1234567,
      phoneType = "MOB",
      phoneNumber = "07973 555 5555",
      createdBy = "JANE.SAM",
      createdTime = LocalDateTime.parse("2024-01-01T12:13"),
    )

    fun contactAddressPhone() = SyncContactAddressPhone(
      // Ask DPS what this Id is
      contactPhoneId = 432135,
      contactAddressId = 123456,
      contactAddressPhoneId = 1234567,
      phoneType = "MOB",
      phoneNumber = "07973 555 5555",
      contactId = 12345,
      createdBy = "JANE.SAM",
      createdTime = LocalDateTime.parse("2024-01-01T12:13"),
    )
  }

  fun stubMigrateContact(response: MigrateContactResponse = migrateContactResponse()) {
    stubFor(
      post("/migrate/contact")
        .willReturn(
          aResponse()
            .withStatus(201)
            .withHeader("Content-Type", "application/json")
            .withBody(ContactPersonDpsApiExtension.objectMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubMigrateContact(nomisPersonId: Long, response: MigrateContactResponse = migrateContactResponse()) {
    stubFor(
      post("/migrate/contact")
        .willReturn(
          aResponse()
            .withStatus(201)
            .withHeader("Content-Type", "application/json")
            .withBody(ContactPersonDpsApiExtension.objectMapper.writeValueAsString(response)),
        ).withRequestBody(matchingJsonPath("$.personId", equalTo(nomisPersonId.toString()))),
    )
  }

  fun stubHealthPing(status: Int) {
    stubFor(
      get("/health/ping").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(if (status == 200) "pong" else "some error")
          .withStatus(status),
      ),
    )
  }

  fun stubCreateContact(response: SyncContact = contact()) {
    stubFor(
      post("/sync/contact")
        .willReturn(
          aResponse()
            .withStatus(201)
            .withHeader("Content-Type", "application/json")
            .withBody(ContactPersonDpsApiExtension.objectMapper.writeValueAsString(response)),
        ),
    )
  }
  fun stubCreatePrisonerContact(response: SyncPrisonerContact = prisonerContact()) {
    stubFor(
      post("/sync/prisoner-contact")
        .willReturn(
          aResponse()
            .withStatus(201)
            .withHeader("Content-Type", "application/json")
            .withBody(ContactPersonDpsApiExtension.objectMapper.writeValueAsString(response)),
        ),
    )
  }
  fun stubCreateContactAddress(response: SyncContactAddress = contactAddress()) {
    stubFor(
      post("/sync/contact-address")
        .willReturn(
          aResponse()
            .withStatus(201)
            .withHeader("Content-Type", "application/json")
            .withBody(ContactPersonDpsApiExtension.objectMapper.writeValueAsString(response)),
        ),
    )
  }
  fun stubCreateContactEmail(response: SyncContactEmail = contactEmail()) {
    stubFor(
      post("/sync/contact-email")
        .willReturn(
          aResponse()
            .withStatus(201)
            .withHeader("Content-Type", "application/json")
            .withBody(ContactPersonDpsApiExtension.objectMapper.writeValueAsString(response)),
        ),
    )
  }
  fun stubCreateContactPhone(response: SyncContactPhone = contactPhone()) {
    stubFor(
      post("/sync/contact-phone")
        .willReturn(
          aResponse()
            .withStatus(201)
            .withHeader("Content-Type", "application/json")
            .withBody(ContactPersonDpsApiExtension.objectMapper.writeValueAsString(response)),
        ),
    )
  }
  fun stubCreateContactAddressPhone(response: SyncContactAddressPhone = contactAddressPhone()) {
    stubFor(
      post("/sync/contact-address-phone")
        .willReturn(
          aResponse()
            .withStatus(201)
            .withHeader("Content-Type", "application/json")
            .withBody(ContactPersonDpsApiExtension.objectMapper.writeValueAsString(response)),
        ),
    )
  }
}
