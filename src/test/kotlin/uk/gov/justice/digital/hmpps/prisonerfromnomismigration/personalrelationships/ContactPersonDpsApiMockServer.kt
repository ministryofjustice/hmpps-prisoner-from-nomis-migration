package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.delete
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.http.HttpStatus
import org.springframework.test.context.junit.jupiter.SpringExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.AddressAndPhones
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.ChangedRestrictionsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.CodedValue
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.ContactsAndRestrictions
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.IdPair
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.MergePrisonerContactRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.MergePrisonerContactResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.MergePrisonerRestrictionsRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.MigrateContactRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.MigrateContactResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.PrisonerContactAndRestrictionIds
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.PrisonerRestrictionDetailsRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.ResetPrisonerContactRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.ResetPrisonerContactResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.ResetPrisonerRestrictionsRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncContact
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncContactAddress
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncContactAddressPhone
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncContactEmail
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncContactIdentity
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncContactPhone
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncContactRestriction
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncCreateContactAddressPhoneRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncCreateContactAddressRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncCreateContactEmailRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncCreateContactIdentityRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncCreateContactPhoneRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncCreateContactRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncCreateContactRestrictionRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncCreateEmploymentRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncCreatePrisonerContactRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncCreatePrisonerContactRestrictionRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncCreatePrisonerRestrictionRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncEmployment
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncPrisonerContact
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncPrisonerContactRestriction
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncPrisonerRelationship
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncPrisonerRestriction
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncRelationshipRestriction
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncUpdateContactAddressPhoneRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncUpdateContactAddressRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncUpdateContactEmailRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncUpdateContactIdentityRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncUpdateContactPhoneRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncUpdateContactRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncUpdateContactRestrictionRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncUpdateEmploymentRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncUpdatePrisonerContactRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncUpdatePrisonerContactRestrictionRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncUpdatePrisonerRestrictionRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.getRequestBodies
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.getRequestBody
import java.time.LocalDate
import java.time.LocalDateTime

class ContactPersonDpsApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val dpsContactPersonServer = ContactPersonDpsApiMockServer()
    lateinit var objectMapper: ObjectMapper

    inline fun <reified T> getRequestBody(pattern: RequestPatternBuilder): T = dpsContactPersonServer.getRequestBody(pattern, objectMapper)
    inline fun <reified T> getRequestBodies(pattern: RequestPatternBuilder): List<T> = dpsContactPersonServer.getRequestBodies(pattern, objectMapper)
  }

  override fun beforeAll(context: ExtensionContext) {
    dpsContactPersonServer.start()
    objectMapper = (SpringExtension.getApplicationContext(context).getBean("jackson2ObjectMapper") as ObjectMapper)
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

    fun mergePrisonerContactRequest() = MergePrisonerContactRequest(
      prisonerContacts = listOf(syncPrisonerRelationship()),
      removedPrisonerNumber = "A1000KT",
      retainedPrisonerNumber = "A1234KT",
    )

    fun mergePrisonerContactResponse() = MergePrisonerContactResponse(
      relationshipsCreated = listOf(
        PrisonerContactAndRestrictionIds(
          contactId = 1234567,
          relationship = IdPair(elementType = IdPair.ElementType.PRISONER_CONTACT, nomisId = 12345, dpsId = 1234567),
          restrictions = listOf(IdPair(elementType = IdPair.ElementType.PRISONER_CONTACT_RESTRICTION, nomisId = 12345, dpsId = 1234567)),
        ),
      ),
      relationshipsRemoved = listOf(),
    )

    fun resetPrisonerContactRequest() = ResetPrisonerContactRequest(
      prisonerContacts = listOf(syncPrisonerRelationship()),
      prisonerNumber = "A1234KT",
    )

    fun resetPrisonerContactResponse() = ResetPrisonerContactResponse(
      relationshipsCreated = listOf(
        PrisonerContactAndRestrictionIds(
          contactId = 1234567,
          relationship = IdPair(elementType = IdPair.ElementType.PRISONER_CONTACT, nomisId = 12345, dpsId = 1234567),
          restrictions = listOf(IdPair(elementType = IdPair.ElementType.PRISONER_CONTACT_RESTRICTION, nomisId = 12345, dpsId = 1234567)),
        ),
      ),
      relationshipsRemoved = listOf(),
    )

    fun syncPrisonerRelationship() = SyncPrisonerRelationship(
      id = 321,
      contactId = 1233,
      contactType = CodedValue(code = "S", description = ""),
      relationshipType = CodedValue(code = "BRO", description = ""),
      emergencyContact = false,
      nextOfKin = false,
      approvedVisitor = false,
      createUsername = "J.SMITH",
      createDateTime = LocalDateTime.parse("2024-01-01T12:13"),
      currentTerm = true,
      active = true,
      restrictions = listOf(
        SyncRelationshipRestriction(
          id = 456,
          restrictionType = CodedValue(code = "BAN", description = ""),
          startDate = LocalDate.now(),
          comment = null,
          expiryDate = null,
          createDateTime = LocalDateTime.parse("2024-01-01T12:13"),
          createUsername = "J.SMITH",
          modifyDateTime = null,
          modifyUsername = null,
        ),
      ),
      expiryDate = null,
      comment = null,
      modifyDateTime = null,
      modifyUsername = null,
      prisonerNumber = "A1234KT",
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
    fun updateContactRequest() = SyncUpdateContactRequest(
      title = "MR",
      lastName = "KOFI",
      firstName = "KWEKU",
      isStaff = false,
      interpreterRequired = false,
      remitter = false,
      updatedBy = "JANE.SAM",
      updatedTime = LocalDateTime.parse("2024-01-01T12:13"),
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

    fun updatePrisonerContactRequest() = SyncUpdatePrisonerContactRequest(
      contactId = 654321,
      prisonerNumber = "A1234KT",
      contactType = "S",
      relationshipType = "BRO",
      emergencyContact = false,
      nextOfKin = false,
      approvedVisitor = false,
      active = true,
      currentTerm = true,
      updatedBy = "J.SMITH",
      updatedTime = LocalDateTime.parse("2024-01-01T12:13"),
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

    fun updateContactAddressRequest() = SyncUpdateContactAddressRequest(
      contactId = 1234567,
      addressType = "MOB",
      primaryAddress = true,
      updatedBy = "JANE.SAM",
      updatedTime = LocalDateTime.parse("2024-01-01T12:13"),
      verified = false,
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

    fun updateContactEmailRequest() = SyncUpdateContactEmailRequest(
      contactId = 1234567,
      emailAddress = "test.test@test.com",
      updatedBy = "JANE.SAM",
      updatedTime = LocalDateTime.parse("2024-01-01T12:13"),
    )

    fun contactEmail() = SyncContactEmail(
      contactEmailId = 1234567,
      emailAddress = "test.test@test.com",
      contactId = 12345,
      createdBy = "JANE.SAM",
      createdTime = LocalDateTime.parse("2024-01-01T12:13"),
    )

    fun createContactEmploymentRequest() = SyncCreateEmploymentRequest(
      contactId = 1234567,
      organisationId = 54432,
      active = true,
      createdBy = "JANE.SAM",
      createdTime = LocalDateTime.parse("2024-01-01T12:13"),
    )

    fun updateContactEmploymentRequest() = SyncUpdateEmploymentRequest(
      contactId = 1234567,
      organisationId = 54432,
      active = true,
      updatedBy = "JANE.SAM",
      updatedTime = LocalDateTime.parse("2024-01-01T12:13"),
    )

    fun contactEmployment() = SyncEmployment(
      employmentId = 1234567,
      organisationId = 54432,
      active = true,
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

    fun updateContactPhoneRequest() = SyncUpdateContactPhoneRequest(
      contactId = 1234567,
      phoneType = "MOB",
      phoneNumber = "07973 555 5555",
      updatedBy = "JANE.SAM",
      updatedTime = LocalDateTime.parse("2024-01-01T12:13"),
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

    fun updateContactAddressPhoneRequest() = SyncUpdateContactAddressPhoneRequest(
      phoneType = "MOB",
      phoneNumber = "07973 555 5555",
      updatedBy = "JANE.SAM",
      updatedTime = LocalDateTime.parse("2024-01-01T12:13"),
    )

    fun contactAddressPhone() = SyncContactAddressPhone(
      contactPhoneId = 432135,
      contactAddressId = 123456,
      contactAddressPhoneId = 1234567,
      phoneType = "MOB",
      phoneNumber = "07973 555 5555",
      contactId = 12345,
      createdBy = "JANE.SAM",
      createdTime = LocalDateTime.parse("2024-01-01T12:13"),
    )

    fun contactIdentity() = SyncContactIdentity(
      contactIdentityId = 1234567,
      identityType = "DL",
      identityValue = "SMITH7737373KT",
      issuingAuthority = "DVLA",
      contactId = 12345,
      createdBy = "JANE.SAM",
      createdTime = LocalDateTime.parse("2024-01-01T12:13"),
    )

    fun createContactIdentityRequest() = SyncCreateContactIdentityRequest(
      contactId = 1234567,
      identityType = "MOB",
      identityValue = "SMITH7737373KT",
      issuingAuthority = "DVLA",
      createdBy = "JANE.SAM",
      createdTime = LocalDateTime.parse("2024-01-01T12:13"),
    )

    fun updateContactIdentityRequest() = SyncUpdateContactIdentityRequest(
      contactId = 1234567,
      identityType = "MOB",
      identityValue = "SMITH7737373KT",
      issuingAuthority = "DVLA",
      updatedBy = "JANE.SAM",
      updatedTime = LocalDateTime.parse("2024-01-01T12:13"),
    )

    fun prisonerContactRestriction() = SyncPrisonerContactRestriction(
      contactId = 1234567,
      prisonerContactId = 654321,
      prisonerContactRestrictionId = 209876,
      prisonerNumber = "A1234KT",

    )

    fun createPrisonerContactRestrictionRequest() = SyncCreatePrisonerContactRestrictionRequest(
      prisonerContactId = 654321,
      restrictionType = "BAN",
      createdBy = "J.SMITH",
      createdTime = LocalDateTime.parse("2024-01-01T12:13"),
    )

    fun updatePrisonerContactRestrictionRequest() = SyncUpdatePrisonerContactRestrictionRequest(
      restrictionType = "BAN",
      updatedBy = "J.SMITH",
      updatedTime = LocalDateTime.parse("2024-01-01T12:13"),
    )

    fun contactRestriction() = SyncContactRestriction(
      contactId = 1234567,
      contactRestrictionId = 209876,
      restrictionType = "BAN",
      startDate = LocalDate.parse("2024-01-01"),
      expiryDate = LocalDate.parse("2024-01-01"),
      createdBy = "J.SMITH",
      createdTime = LocalDateTime.parse("2024-01-01T12:13"),
    )

    fun createContactRestrictionRequest() = SyncCreateContactRestrictionRequest(
      contactId = 654321,
      restrictionType = "BAN",
      createdBy = "J.SMITH",
      createdTime = LocalDateTime.parse("2024-01-01T12:13"),
    )

    fun updateContactRestrictionRequest() = SyncUpdateContactRestrictionRequest(
      contactId = 654321,
      restrictionType = "BAN",
      updatedBy = "J.SMITH",
      updatedTime = LocalDateTime.parse("2024-01-01T12:13"),
    )

    fun createPrisonerRestrictionRequest() = SyncCreatePrisonerRestrictionRequest(
      prisonerNumber = "A1234KT",
      restrictionType = "BAN",
      effectiveDate = LocalDate.parse("2024-01-01"),
      currentTerm = true,
      authorisedUsername = "J.SMITH",
      createdBy = "J.SMITH",
      createdTime = LocalDateTime.parse("2024-01-01T12:13"),
      expiryDate = LocalDate.parse("2024-12-31"),
      commentText = "Test restriction",
    )

    fun dpsPrisonerRestriction() = SyncPrisonerRestriction(
      prisonerRestrictionId = 12345,
      prisonerNumber = "A1234KT",
      restrictionType = "BAN",
      effectiveDate = LocalDate.parse("2024-01-01"),
      authorisedUsername = "J.SMITH",
      currentTerm = true,
      createdBy = "J.SMITH",
      createdTime = LocalDateTime.parse("2024-01-01T12:13"),
      expiryDate = LocalDate.parse("2024-12-31"),
      commentText = "Test restriction",
    )

    fun updatePrisonerRestrictionRequest() = SyncUpdatePrisonerRestrictionRequest(
      prisonerNumber = "A1234KT",
      restrictionType = "BAN",
      effectiveDate = LocalDate.parse("2024-01-01"),
      authorisedUsername = "J.SMITH",
      updatedBy = "J.SMITH",
      updatedTime = LocalDateTime.parse("2024-01-01T12:13"),
      expiryDate = LocalDate.parse("2024-12-31"),
      currentTerm = true,
      commentText = "Test restriction",
    )

    fun resetPrisonerRestrictionsRequest() = ResetPrisonerRestrictionsRequest(
      prisonerNumber = "A1234KT",
      restrictions = listOf(
        PrisonerRestrictionDetailsRequest(
          restrictionType = "BAN",
          effectiveDate = LocalDate.now(),
          authorisedUsername = "T.SMITH",
          currentTerm = true,
          createdBy = "T.SMITH",
          createdTime = LocalDateTime.now(),
          expiryDate = null,
          commentText = null,
          updatedBy = null,
          updatedTime = null,
        ),
      ),
    )

    fun mergePrisonerRestrictionsRequest() = MergePrisonerRestrictionsRequest(
      keepingPrisonerNumber = "A1234AA",
      removingPrisonerNumber = "B1234BB",
      restrictions = listOf(
        PrisonerRestrictionDetailsRequest(
          restrictionType = "BAN",
          effectiveDate = LocalDate.now(),
          authorisedUsername = "T.SMITH",
          currentTerm = true,
          createdBy = "T.SMITH",
          createdTime = LocalDateTime.now(),
          expiryDate = null,
          commentText = null,
          updatedBy = null,
          updatedTime = null,
        ),
      ),
    )

    fun changedRestrictionsResponse() = ChangedRestrictionsResponse(
      hasChanged = true,
      createdRestrictions = listOf(1234L),
      deletedRestrictions = listOf(5678L),
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
  fun stubUpdateContact(contactId: Long, response: SyncContact = contact()) {
    stubFor(
      put("/sync/contact/$contactId")
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(ContactPersonDpsApiExtension.objectMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubDeleteContact(contactId: Long, status: Int = 204) {
    stubFor(
      delete("/sync/contact/$contactId")
        .willReturn(
          aResponse()
            .withStatus(status)
            .withHeader("Content-Type", "application/json"),
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
  fun stubUpdatePrisonerContact(prisonerContactId: Long, response: SyncPrisonerContact = prisonerContact()) {
    stubFor(
      put("/sync/prisoner-contact/$prisonerContactId")
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(ContactPersonDpsApiExtension.objectMapper.writeValueAsString(response)),
        ),
    )
  }
  fun stubCreatePrisonerContact(httpStatus: HttpStatus) {
    stubFor(
      post("/sync/prisoner-contact")
        .willReturn(
          aResponse()
            .withStatus(httpStatus.value())
            .withHeader("Content-Type", "application/json")
            .withBody(ContactPersonDpsApiExtension.objectMapper.writeValueAsString(ErrorResponse(status = httpStatus.value()))),
        ),
    )
  }
  fun stubDeletePrisonerContact(prisonerContactId: Long, status: Int = 204) {
    stubFor(
      delete("/sync/prisoner-contact/$prisonerContactId")
        .willReturn(
          aResponse()
            .withStatus(status)
            .withHeader("Content-Type", "application/json"),
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

  fun stubUpdateContactAddress(addressId: Long, response: SyncContactAddress = contactAddress()) {
    stubFor(
      put("/sync/contact-address/$addressId")
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(ContactPersonDpsApiExtension.objectMapper.writeValueAsString(response)),
        ),
    )
  }
  fun stubDeleteContactAddress(addressId: Long, status: Int = 204) {
    stubFor(
      delete("/sync/contact-address/$addressId")
        .willReturn(
          aResponse()
            .withStatus(status)
            .withHeader("Content-Type", "application/json"),
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
  fun stubUpdateContactEmail(contactEmailId: Long, response: SyncContactEmail = contactEmail()) {
    stubFor(
      put("/sync/contact-email/$contactEmailId")
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(ContactPersonDpsApiExtension.objectMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubDeleteContactEmail(contactEmailId: Long, status: Int = 204) {
    stubFor(
      delete("/sync/contact-email/$contactEmailId")
        .willReturn(
          aResponse()
            .withStatus(status)
            .withHeader("Content-Type", "application/json"),
        ),
    )
  }
  fun stubCreateContactEmployment(response: SyncEmployment = contactEmployment()) {
    stubFor(
      post("/sync/employment")
        .willReturn(
          aResponse()
            .withStatus(201)
            .withHeader("Content-Type", "application/json")
            .withBody(ContactPersonDpsApiExtension.objectMapper.writeValueAsString(response)),
        ),
    )
  }
  fun stubUpdateContactEmployment(contactEmploymentId: Long, response: SyncEmployment = contactEmployment()) {
    stubFor(
      put("/sync/employment/$contactEmploymentId")
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(ContactPersonDpsApiExtension.objectMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubDeleteContactEmployment(contactEmploymentId: Long, status: Int = 204) {
    stubFor(
      delete("/sync/employment/$contactEmploymentId")
        .willReturn(
          aResponse()
            .withStatus(status)
            .withHeader("Content-Type", "application/json"),
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
  fun stubUpdateContactPhone(contactPhoneId: Long, response: SyncContactPhone = contactPhone()) {
    stubFor(
      put("/sync/contact-phone/$contactPhoneId")
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(ContactPersonDpsApiExtension.objectMapper.writeValueAsString(response)),
        ),
    )
  }
  fun stubDeleteContactPhone(contactPhoneId: Long, status: Int = 204) {
    stubFor(
      delete("/sync/contact-phone/$contactPhoneId")
        .willReturn(
          aResponse()
            .withStatus(status)
            .withHeader("Content-Type", "application/json"),
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
  fun stubUpdateContactAddressPhone(contactAddressPhoneId: Long, response: SyncContactAddressPhone = contactAddressPhone()) {
    stubFor(
      put("/sync/contact-address-phone/$contactAddressPhoneId")
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(ContactPersonDpsApiExtension.objectMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubDeleteContactAddressPhone(contactAddressPhoneId: Long, status: Int = 204) {
    stubFor(
      delete("/sync/contact-address-phone/$contactAddressPhoneId")
        .willReturn(
          aResponse()
            .withStatus(status)
            .withHeader("Content-Type", "application/json"),
        ),
    )
  }
  fun stubCreateContactIdentity(response: SyncContactIdentity = contactIdentity()) {
    stubFor(
      post("/sync/contact-identity")
        .willReturn(
          aResponse()
            .withStatus(201)
            .withHeader("Content-Type", "application/json")
            .withBody(ContactPersonDpsApiExtension.objectMapper.writeValueAsString(response)),
        ),
    )
  }
  fun stubUpdateContactIdentity(contactIdentityId: Long, response: SyncContactIdentity = contactIdentity()) {
    stubFor(
      put("/sync/contact-identity/$contactIdentityId")
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(ContactPersonDpsApiExtension.objectMapper.writeValueAsString(response)),
        ),
    )
  }
  fun stubDeleteContactIdentity(contactIdentityId: Long, status: Int = 204) {
    stubFor(
      delete("/sync/contact-identity/$contactIdentityId")
        .willReturn(
          aResponse()
            .withStatus(status)
            .withHeader("Content-Type", "application/json"),
        ),
    )
  }
  fun stubCreatePrisonerContactRestriction(response: SyncPrisonerContactRestriction = prisonerContactRestriction()) {
    stubFor(
      post("/sync/prisoner-contact-restriction")
        .willReturn(
          aResponse()
            .withStatus(201)
            .withHeader("Content-Type", "application/json")
            .withBody(ContactPersonDpsApiExtension.objectMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubUpdatePrisonerContactRestriction(dpsPrisonerContactRestrictionId: Long, response: SyncPrisonerContactRestriction = prisonerContactRestriction()) {
    stubFor(
      put("/sync/prisoner-contact-restriction/$dpsPrisonerContactRestrictionId")
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(ContactPersonDpsApiExtension.objectMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubDeletePrisonerContactRestriction(dpsPrisonerContactRestrictionId: Long, status: Int = 204) {
    stubFor(
      delete("/sync/prisoner-contact-restriction/$dpsPrisonerContactRestrictionId")
        .willReturn(
          aResponse()
            .withStatus(status)
            .withHeader("Content-Type", "application/json"),
        ),
    )
  }

  fun stubCreateContactRestriction(response: SyncContactRestriction = contactRestriction()) {
    stubFor(
      post("/sync/contact-restriction")
        .willReturn(
          aResponse()
            .withStatus(201)
            .withHeader("Content-Type", "application/json")
            .withBody(ContactPersonDpsApiExtension.objectMapper.writeValueAsString(response)),
        ),
    )
  }
  fun stubUpdateContactRestriction(contactRestrictionId: Long, response: SyncContactRestriction = contactRestriction()) {
    stubFor(
      put("/sync/contact-restriction/$contactRestrictionId")
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(ContactPersonDpsApiExtension.objectMapper.writeValueAsString(response)),
        ),
    )
  }
  fun stubDeleteContactRestriction(contactRestrictionId: Long, status: Int = 204) {
    stubFor(
      delete("/sync/contact-restriction/$contactRestrictionId")
        .willReturn(
          aResponse()
            .withStatus(status)
            .withHeader("Content-Type", "application/json"),
        ),
    )
  }
  fun stubReplaceMergedPrisonerContacts(response: MergePrisonerContactResponse = mergePrisonerContactResponse()) {
    stubFor(
      post("/sync/admin/merge")
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(ContactPersonDpsApiExtension.objectMapper.writeValueAsString(response)),
        ),
    )
  }
  fun stubResetPrisonerContacts(response: ResetPrisonerContactResponse = resetPrisonerContactResponse()) {
    stubFor(
      post("/sync/admin/reset")
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(ContactPersonDpsApiExtension.objectMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubCreatePrisonerRestriction(response: SyncPrisonerRestriction = dpsPrisonerRestriction()) {
    stubFor(
      post("/sync/prisoner-restriction")
        .willReturn(
          aResponse()
            .withStatus(201)
            .withHeader("Content-Type", "application/json")
            .withBody(ContactPersonDpsApiExtension.objectMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubUpdatePrisonerRestriction(prisonerRestrictionId: Long, response: SyncPrisonerRestriction = dpsPrisonerRestriction()) {
    stubFor(
      put("/sync/prisoner-restriction/$prisonerRestrictionId")
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(ContactPersonDpsApiExtension.objectMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubDeletePrisonerRestriction(prisonerRestrictionId: Long, status: Int = 204) {
    stubFor(
      delete("/sync/prisoner-restriction/$prisonerRestrictionId")
        .willReturn(
          aResponse()
            .withStatus(status)
            .withHeader("Content-Type", "application/json"),
        ),
    )
  }

  fun stubResetPrisonerRestrictions(response: ChangedRestrictionsResponse = changedRestrictionsResponse()) {
    stubFor(
      post("/prisoner-restrictions/reset")
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(ContactPersonDpsApiExtension.objectMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubMergePrisonerRestrictions(response: ChangedRestrictionsResponse = changedRestrictionsResponse()) {
    stubFor(
      post("/prisoner-restrictions/merge")
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(ContactPersonDpsApiExtension.objectMapper.writeValueAsString(response)),
        ),
    )
  }
}
