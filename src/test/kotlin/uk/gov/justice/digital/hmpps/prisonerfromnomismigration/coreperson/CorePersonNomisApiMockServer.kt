package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CoreOffender
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CorePerson
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.OffenderAddress
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.OffenderAddressUsage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.OffenderPhoneNumber
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import java.time.LocalDate

@Component
class CorePersonNomisApiMockServer(private val objectMapper: ObjectMapper) {
  fun stubGetCorePerson(
    prisonNumber: String = "A1234BC",
    corePerson: CorePerson = corePerson(prisonNumber = prisonNumber),
  ) {
    nomisApi.stubFor(
      get(urlEqualTo("/core-person/$prisonNumber")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(
            objectMapper.writeValueAsString(corePerson),
          ),
      ),
    )
  }

  fun stubGetCorePerson(
    prisonNumber: String = "A1234BC",
    status: HttpStatus,
    error: ErrorResponse = ErrorResponse(status = status.value()),
  ) {
    nomisApi.stubFor(
      get(urlEqualTo("/core-person/$prisonNumber")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = nomisApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = nomisApi.verify(count, pattern)
}

fun corePerson(prisonNumber: String = "A1234BC"): CorePerson = CorePerson(
  prisonNumber = prisonNumber,

  offenders = listOf(
    CoreOffender(
      offenderId = 1,
      firstName = "JOHN",
      lastName = "SMITH",
      workingName = true,
      title = CodeDescription(code = "MR", description = "Mr"),
      middleName1 = "FRED",
      middleName2 = "JAMES",
      dateOfBirth = LocalDate.parse("1980-01-01"),
      birthPlace = "LONDON",
      birthCountry = CodeDescription(code = "ENG", description = "England"),
      ethnicity = CodeDescription(code = "BLACK", description = "Black"),
      sex = CodeDescription(code = "M", description = "Male"),
    ),
  ),
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
      validatedPAF = false,
      primaryAddress = true,
      mailAddress = true,
      usages = listOf(
        OffenderAddressUsage(
          addressId = 1,
          usage = CodeDescription(code = "HOME", description = "Home"),
          active = true,
        ),
      ),
    ),
  ),
  activeFlag = true,
  identifiers = emptyList(),
  sentenceStartDates = emptyList(),
  emailAddresses = emptyList(),
  nationalities = emptyList(),
  nationalityDetails = emptyList(),
  sexualOrientations = emptyList(),
  disabilities = emptyList(),
  interestsToImmigration = emptyList(),
  beliefs = emptyList(),
  inOutStatus = "OUT",
)
