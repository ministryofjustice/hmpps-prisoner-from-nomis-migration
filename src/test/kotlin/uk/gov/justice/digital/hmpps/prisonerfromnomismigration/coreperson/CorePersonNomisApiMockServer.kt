package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CoreOffender
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CorePerson
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.Identifier
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.NomisAudit
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderAddress
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderAddressUsage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderBelief
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderDisability
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderEmailAddress
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderInterestToImmigration
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderNationality
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderPhoneNumber
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderSexualOrientation
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import java.time.LocalDate
import java.time.LocalDateTime

@Component
class CorePersonNomisApiMockServer(private val jsonMapper: JsonMapper) {
  fun stubGetCorePerson(
    prisonNumber: String = "A1234BC",
    corePerson: CorePerson = corePerson(prisonNumber = prisonNumber),
    status: HttpStatus = HttpStatus.OK,
    error: ErrorResponse = ErrorResponse(status = status.value()),
  ) {
    nomisApi.stubFor(
      get(urlEqualTo("/core-person/$prisonNumber")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(
            jsonMapper.writeValueAsString(if (status == HttpStatus.OK) corePerson else error),
          ),
      ),
    )
  }
  fun stubGetOffenderReligions(
    prisonNumber: String = "A1234BC",
    religions: List<OffenderBelief> = beliefs(),
    status: HttpStatus = HttpStatus.OK,
    error: ErrorResponse = ErrorResponse(status = status.value()),
  ) {
    nomisApi.stubFor(
      get(urlEqualTo("/core-person/$prisonNumber/religions")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(
            jsonMapper.writeValueAsString(if (status == HttpStatus.OK) religions else error),
          ),
      ),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = nomisApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = nomisApi.verify(count, pattern)
}

fun corePerson(prisonNumber: String = "A1234BC"): CorePerson = CorePerson(
  prisonNumber = prisonNumber,
  activeFlag = true,
  inOutStatus = "OUT",
  offenders = listOf(
    CoreOffender(
      offenderId = 1,
      title = CodeDescription(code = "MR", description = "Mr"),
      firstName = "JOHN",
      lastName = "SMITH",
      workingName = true,
      middleName1 = "FRED",
      middleName2 = "JAMES",
      dateOfBirth = LocalDate.parse("1980-01-01"),
      birthPlace = "LONDON",
      birthCountry = CodeDescription(code = "ENG", description = "England"),
      ethnicity = CodeDescription(code = "BLACK", description = "Black"),
      sex = CodeDescription(code = "M", description = "Male"),
      nameType = CodeDescription(code = "MAID", description = "Maiden"),
      identifiers = listOf(
        Identifier(
          sequence = 1,
          type = CodeDescription("PNC", "PNC Number"),
          identifier = "20/0071818T",
          issuedAuthority = "Met Police",
          issuedDate = LocalDate.parse("2020-01-01"),
          verified = true,
        ),
      ),
    ),
  ),
  sentenceStartDates = listOf(LocalDate.parse("1980-01-01")),
  phoneNumbers = listOf(
    OffenderPhoneNumber(
      phoneId = 1,
      number = "0114555555",
      type = CodeDescription(code = "HOME", description = "Home"),
      extension = "1234",
    ),
  ),
  addresses = listOf(
    OffenderAddress(
      addressId = 1,
      phoneNumbers = listOf(
        OffenderPhoneNumber(
          phoneId = 2,
          number = "0114555555",
          type = CodeDescription(code = "HOME", description = "Home"),
          extension = "1234",
        ),
      ),
      validatedPAF = true,
      primaryAddress = true,
      mailAddress = true,
      usages = listOf(OffenderAddressUsage(addressId = 201, usage = CodeDescription("HOME", "Home"), active = true)),
      flat = "Flat 1B",
      premise = "Pudding Court",
      street = "High Mound",
      locality = "Broomhill",
      postcode = "S1 5GG",
      city = CodeDescription("25343", "Sheffield"),
      county = CodeDescription("S.YORKSHIRE", "South Yorkshire"),
      country = CodeDescription("ENG", "England"),
      noFixedAddress = true,
      comment = "Use this address",
      startDate = LocalDate.parse("1987-01-01"),
      endDate = LocalDate.parse("2024-02-01"),
    ),
  ),
  emailAddresses = listOf(
    OffenderEmailAddress(
      emailAddressId = 130,
      email = "test@test.justice.gov.uk",
    ),
  ),
  nationalities = listOf(
    OffenderNationality(
      bookingId = 1125444,
      nationality = CodeDescription("BRIT", "British"),
      startDateTime = LocalDateTime.parse("2016-08-18T19:58:23"),
      latestBooking = true,
    ),
  ),
  nationalityDetails = emptyList(),
  sexualOrientations = listOf(
    OffenderSexualOrientation(
      bookingId = 1125444,
      sexualOrientation = CodeDescription("HET", "Heterosexual"),
      startDateTime = LocalDateTime.parse("2016-08-19T19:58:23"),
      latestBooking = true,
    ),
  ),
  disabilities = listOf(
    OffenderDisability(
      bookingId = 1125444,
      disability = true,
      startDateTime = LocalDateTime.parse("2016-08-19T19:58:23"),
      latestBooking = true,
    ),
  ),
  interestsToImmigration = listOf(
    OffenderInterestToImmigration(
      bookingId = 1125444,
      startDateTime = LocalDateTime.parse("2016-08-19T19:58:23"),
      interestToImmigration = true,
      latestBooking = true,
    ),
  ),
  beliefs = beliefs(),
)

fun beliefs() = listOf(
  OffenderBelief(
    beliefId = 2,
    belief = CodeDescription("DRU", "Druid"),
    startDate = LocalDate.parse("2016-08-02"),
    verified = true,
    audit = NomisAudit(
      createDatetime = LocalDateTime.parse("2016-08-01T10:55:00"),
      createUsername = "KOFEADDY",
      createDisplayName = "KOFE ADDY",
    ),
    changeReason = true,
    comments = "No longer believes in Zoroastrianism",
  ),
)
