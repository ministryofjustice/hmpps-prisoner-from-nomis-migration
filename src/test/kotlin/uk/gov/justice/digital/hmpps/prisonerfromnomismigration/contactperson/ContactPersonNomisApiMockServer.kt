package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.ContactForPrisoner
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.ContactPerson
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.ContactRestriction
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.ContactRestrictionEnteredStaff
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.NomisAudit
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.PersonAddress
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.PersonContact
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.PersonEmailAddress
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.PersonEmployment
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.PersonEmploymentCorporate
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.PersonIdResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.PersonIdentifier
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.PersonPhoneNumber
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.pageContent
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Component
class ContactPersonNomisApiMockServer(private val objectMapper: ObjectMapper) {
  fun stubGetPerson(
    personId: Long = 123456,
    person: ContactPerson = contactPerson(),
  ) {
    nomisApi.stubFor(
      get(urlEqualTo("/persons/$personId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(objectMapper.writeValueAsString(person)),
      ),
    )
  }

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
          .withBody(objectMapper.writeValueAsString(error)),
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

  fun verify(pattern: RequestPatternBuilder) = nomisApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = nomisApi.verify(count, pattern)

  fun pagePersonIdResponse(
    count: Long,
    content: List<PersonIdResponse>,
  ) = pageContent(
    objectMapper = objectMapper,
    content = content,
    pageSize = 1L,
    pageNumber = 0L,
    totalElements = count,
    size = 1,
  )
}

fun contactPerson(): ContactPerson = ContactPerson(
  personId = 123456,
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
  contacts = listOf(
    PersonContact(
      id = 1,
      relationshipType = CodeDescription(code = "BOF", description = "Boyfriend"),
      contactType = CodeDescription(code = "S", description = "Social/ Family"),
      active = true,
      emergencyContact = true,
      nextOfKin = false,
      approvedVisitor = false,
      prisoner = ContactForPrisoner(bookingId = 1, offenderNo = "A1234KT", lastName = "SMITH", firstName = "JOHN", bookingSequence = 1),
      restrictions = listOf(ContactRestriction(id = 1, type = CodeDescription(code = "BAN", description = "Banned"), enteredStaff = ContactRestrictionEnteredStaff(staffId = 1, username = "Q1251T"), effectiveDate = LocalDate.parse("2020-01-01"), audit = nomisAudit())),
      audit = nomisAudit(),
    ),
  ),
  restrictions = listOf(ContactRestriction(id = 2, type = CodeDescription(code = "BAN", description = "Banned"), enteredStaff = ContactRestrictionEnteredStaff(staffId = 1, username = "Q1251T"), effectiveDate = LocalDate.parse("2020-01-01"), audit = nomisAudit())),
)

fun nomisAudit() = NomisAudit(
  createDatetime = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
  createUsername = "Q1251T",
)
