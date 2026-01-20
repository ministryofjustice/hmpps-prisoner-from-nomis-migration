package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.ContactForPerson
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.ContactForPrisoner
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.ContactPerson
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.ContactRestriction
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.ContactRestrictionEnteredStaff
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.NomisAudit
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PersonAddress
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PersonContact
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PersonEmailAddress
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PersonEmployment
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PersonEmploymentCorporate
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PersonIdResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PersonIdentifier
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PersonPhoneNumber
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerContact
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerDetails
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerRestriction
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerRestrictionIdResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerWithContacts
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerWithRestrictions
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.pageContent
import java.time.LocalDate
import java.time.LocalDateTime

@Component
class ContactPersonNomisApiMockServer(private val jsonMapper: JsonMapper) {
  fun stubGetPerson(
    personId: Long = 123456,
    person: ContactPerson = contactPerson(),
  ) {
    nomisApi.stubFor(
      get(urlEqualTo("/persons/$personId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(jsonMapper.writeValueAsString(person)),
      ),
    )
  }
  fun stubGetPerson(
    person: ContactPerson = contactPerson(),
  ) = stubGetPerson(personId = person.personId, person)

  fun stubGetPerson(
    personId: Long = 123456,
    status: HttpStatus,
    error: ErrorResponse = ErrorResponse(status = status.value()),
  ) {
    nomisApi.stubFor(
      get(urlEqualTo("/persons/$personId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubGetContact(
    contactId: Long = 123456,
    contact: PersonContact = personContact(),
  ) {
    nomisApi.stubFor(
      get(urlEqualTo("/contact/$contactId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(jsonMapper.writeValueAsString(contact)),
      ),
    )
  }

  fun stubGetPersonIdsToMigrate(
    count: Long = 1,
    content: List<PersonIdResponse> = listOf(
      PersonIdResponse(123456),
    ),
  ) {
    nomisApi.stubFor(
      get(urlPathEqualTo("/persons/ids")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(pagePersonIdResponse(count = count, content = content)),
      ),
    )
  }

  fun stubContactsForPrisoner(
    offenderNo: String,
    contacts: PrisonerWithContacts = prisonerWithContacts(),
  ) {
    nomisApi.stubFor(
      get(urlPathEqualTo("/prisoners/$offenderNo/contacts")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(jsonMapper.writeValueAsString(contacts)),
      ),
    )
  }
  fun stubGetPrisonerDetails(offenderNo: String, prisonerDetails: PrisonerDetails = prisonerDetails()) {
    nomisApi.stubFor(
      get(urlEqualTo("/prisoners/$offenderNo")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(jsonMapper.writeValueAsString(prisonerDetails)),
      ),
    )
  }

  fun stubGetPrisonerRestrictionById(restrictionId: Long, response: PrisonerRestriction = nomisPrisonerRestriction()) {
    nomisApi.stubFor(
      get(urlEqualTo("/prisoners/restrictions/$restrictionId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(jsonMapper.writeValueAsString(response)),
      ),
    )
  }

  fun stubGetPrisonerRestrictions(offenderNo: String, response: PrisonerWithRestrictions = PrisonerWithRestrictions(restrictions = listOf(nomisPrisonerRestriction()))) {
    nomisApi.stubFor(
      get(urlPathEqualTo("/prisoners/$offenderNo/restrictions")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(jsonMapper.writeValueAsString(response)),
      ),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = nomisApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = nomisApi.verify(count, pattern)

  fun pagePersonIdResponse(
    count: Long,
    content: List<PersonIdResponse>,
  ) = pageContent(
    jsonMapper = jsonMapper,
    content = content,
    pageSize = 1L,
    pageNumber = 0L,
    totalElements = count,
    size = 1,
  )
  fun pageResponse(
    count: Long,
    content: List<PrisonerRestrictionIdResponse>,
  ) = pageContent(
    jsonMapper = jsonMapper,
    content = content,
    pageSize = 1L,
    pageNumber = 0L,
    totalElements = count,
    size = 1,
  )
}

fun contactPerson(personId: Long = 123456): ContactPerson = ContactPerson(
  personId = personId,
  firstName = "KWAME",
  lastName = "KOBE",
  interpreterRequired = false,
  audit = nomisAudit(),
  phoneNumbers = listOf(PersonPhoneNumber(phoneId = 1, number = "0114555555", type = CodeDescription(code = "HOME", description = "Home"), audit = nomisAudit())),
  addresses = listOf(PersonAddress(addressId = 1, phoneNumbers = listOf(PersonPhoneNumber(phoneId = 2, number = "0114555555", type = CodeDescription(code = "HOME", description = "Home"), audit = nomisAudit())), validatedPAF = false, primaryAddress = true, mailAddress = true, audit = nomisAudit())),
  emailAddresses = listOf(PersonEmailAddress(emailAddressId = 1, email = "test@justice.gov.uk", audit = nomisAudit())),
  employments = listOf(PersonEmployment(sequence = 1, active = true, corporate = PersonEmploymentCorporate(id = 1, name = "Police"), audit = nomisAudit())),
  identifiers = listOf(
    PersonIdentifier(
      sequence = 1,
      type = CodeDescription(code = "DL", description = "Driving Licence"),
      identifier = "SMITH1717171",
      issuedAuthority = "DVLA",
      audit = nomisAudit(),
    ),
  ),
  contacts = listOf(personContact(contactId = 1)),
  restrictions = listOf(ContactRestriction(id = 2, type = CodeDescription(code = "BAN", description = "Banned"), enteredStaff = ContactRestrictionEnteredStaff(staffId = 1, username = "Q1251T"), effectiveDate = LocalDate.parse("2020-01-01"), audit = nomisAudit())),
)

fun personContact(contactId: Long = 1) = PersonContact(
  id = contactId,
  relationshipType = CodeDescription(code = "BOF", description = "Boyfriend"),
  contactType = CodeDescription(code = "S", description = "Social/ Family"),
  active = true,
  emergencyContact = true,
  nextOfKin = false,
  approvedVisitor = false,
  prisoner = ContactForPrisoner(bookingId = 1, offenderNo = "A1234KT", lastName = "SMITH", firstName = "JOHN", bookingSequence = 1),
  restrictions = listOf(ContactRestriction(id = 1, type = CodeDescription(code = "BAN", description = "Banned"), enteredStaff = ContactRestrictionEnteredStaff(staffId = 1, username = "Q1251T"), effectiveDate = LocalDate.parse("2020-01-01"), audit = nomisAudit())),
  audit = nomisAudit(),
)

fun ContactPerson.withAddress(address: PersonAddress): ContactPerson = copy(addresses = listOf(address))
fun ContactPerson.withAddress(addressId: Long, phone: PersonPhoneNumber): ContactPerson = copy(
  addresses = listOf(
    PersonAddress(
      addressId = addressId,
      phoneNumbers = listOf(phone),
      mailAddress = true,
      primaryAddress = true,
      validatedPAF = true,
      audit = NomisAudit(
        createUsername = "J.SPEAK",
        createDatetime = LocalDateTime.parse("2024-09-01T13:31"),
      ),
    ),
  ),
)
fun ContactPerson.withPhoneNumber(phone: PersonPhoneNumber): ContactPerson = copy(phoneNumbers = listOf(phone))
fun ContactPerson.withEmailAddress(phone: PersonEmailAddress): ContactPerson = copy(emailAddresses = listOf(phone))
fun ContactPerson.withIdentifier(identifier: PersonIdentifier): ContactPerson = copy(identifiers = listOf(identifier))
fun ContactPerson.withEmployment(employment: PersonEmployment): ContactPerson = copy(employments = listOf(employment))
fun ContactPerson.withContactRestriction(restriction: ContactRestriction): ContactPerson = copy(restrictions = listOf(restriction))
fun ContactPerson.withContact(contact: PersonContact): ContactPerson = copy(contacts = listOf(contact))
fun ContactPerson.withContact(contactId: Long, offenderNo: String, restriction: ContactRestriction): ContactPerson = copy(
  contacts = listOf(
    PersonContact(
      id = contactId,
      relationshipType = CodeDescription(code = "BOF", description = "Boyfriend"),
      contactType = CodeDescription(code = "S", description = "Social/ Family"),
      active = true,
      emergencyContact = true,
      nextOfKin = false,
      approvedVisitor = false,
      prisoner = ContactForPrisoner(bookingId = 1, offenderNo = offenderNo, lastName = "SMITH", firstName = "JOHN", bookingSequence = 1),
      restrictions = listOf(restriction),
      audit = nomisAudit(),
    ),
  ),
)

fun nomisAudit() = NomisAudit(
  createDatetime = LocalDateTime.now(),
  createUsername = "Q1251T",
)

fun prisonerWithContacts() = PrisonerWithContacts(
  contacts = listOf(prisonerWithContact()),
)

fun prisonerWithContact() = PrisonerContact(
  id = 1,
  relationshipType = CodeDescription(code = "BOF", description = "Boyfriend"),
  contactType = CodeDescription(code = "S", description = "Social/ Family"),
  active = true,
  emergencyContact = true,
  nextOfKin = false,
  approvedVisitor = false,
  restrictions = listOf(prisonerWithContactRestriction()),
  audit = nomisAudit(),
  bookingId = 1234,
  bookingSequence = 1,
  person = ContactForPerson(
    personId = 4321,
    lastName = "BRIGHT",
    firstName = "JANE",
  ),
  expiryDate = null,
  comment = null,
)

fun prisonerWithContactRestriction() = ContactRestriction(
  id = 1,
  type = CodeDescription(code = "BAN", description = "Banned"),
  enteredStaff = ContactRestrictionEnteredStaff(staffId = 1, username = "Q1251T"),
  effectiveDate = LocalDate.parse("2020-01-01"),
  audit = nomisAudit(),
)

fun prisonerDetails(): PrisonerDetails = PrisonerDetails(offenderNo = "A1234KT", offenderId = 5678, bookingId = 1234, location = "MDI", active = true)

fun nomisPrisonerRestriction() = PrisonerRestriction(
  id = 1234,
  bookingId = 456,
  bookingSequence = 1,
  offenderNo = "A1234KT",
  type = CodeDescription("BAN", "Banned"),
  effectiveDate = LocalDate.now(),
  enteredStaff = ContactRestrictionEnteredStaff(1234, "T.SMITH"),
  authorisedStaff = ContactRestrictionEnteredStaff(1235, "M.SMITH"),
  audit = nomisAudit(),
)
