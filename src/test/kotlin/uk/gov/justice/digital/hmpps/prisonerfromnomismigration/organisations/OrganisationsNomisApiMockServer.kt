package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CorporateAddress
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CorporateInternetAddress
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CorporateOrganisation
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CorporatePhoneNumber
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.NomisAudit
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import java.time.LocalDate
import java.time.LocalDateTime

@Component
class OrganisationsNomisApiMockServer(private val jsonMapper: JsonMapper) {
  fun stubGetCorporateOrganisation(
    corporateId: Long = 123456,
    corporate: CorporateOrganisation = corporateOrganisation(),
  ) {
    nomisApi.stubFor(
      get(urlEqualTo("/corporates/$corporateId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(jsonMapper.writeValueAsString(corporate)),
      ),
    )
  }
  fun verify(pattern: RequestPatternBuilder) = nomisApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = nomisApi.verify(count, pattern)
}

fun corporateOrganisation(corporateId: Long = 123456): CorporateOrganisation = CorporateOrganisation(
  id = corporateId,
  name = "Boots",
  active = true,
  phoneNumbers = emptyList(),
  addresses = emptyList(),
  internetAddresses = emptyList(),
  types = emptyList(),
  audit = nomisAudit(),
)

fun CorporateOrganisation.withAddress(address: CorporateAddress = corporateAddress()): CorporateOrganisation = copy(addresses = listOf(address))
fun corporateAddress(): CorporateAddress = CorporateAddress(
  id = 12345,
  phoneNumbers = emptyList(),
  comment = "nice area",
  validatedPAF = false,
  primaryAddress = true,
  mailAddress = true,
  noFixedAddress = false,
  type = CodeDescription("HOME", "Home Address"),
  flat = "Flat 1",
  premise = "Brown Court",
  locality = "Broomhill",
  street = "Broomhill Street",
  postcode = "S1 6GG",
  city = CodeDescription("12345", "Sheffield"),
  county = CodeDescription("S.YORKSHIRE", "South Yorkshire"),
  country = CodeDescription("GBR", "United Kingdom"),
  startDate = LocalDate.parse("2021-01-01"),
  endDate = LocalDate.parse("2025-01-01"),
  isServices = true,
  contactPersonName = "Bob Brown",
  businessHours = "10am to 10pm Monday to Friday",
  audit = NomisAudit(
    createUsername = "J.SPEAK",
    createDatetime = LocalDateTime.parse("2024-09-01T13:31"),
    modifyUserId = "T.SMITH",
    modifyDatetime = LocalDateTime.parse("2024-10-01T13:31"),
  ),
)
fun CorporateOrganisation.withPhone(phone: CorporatePhoneNumber = corporatePhone()): CorporateOrganisation = copy(phoneNumbers = listOf(phone))
fun CorporateAddress.withPhone(phone: CorporatePhoneNumber = corporatePhone()): CorporateAddress = copy(phoneNumbers = listOf(phone))
fun corporatePhone(): CorporatePhoneNumber = CorporatePhoneNumber(
  id = 12345,
  type = CodeDescription("HOME", "Home Address"),
  number = "0114 555 5555",
  extension = "ext 123",
  audit = NomisAudit(
    createUsername = "J.SPEAK",
    createDatetime = LocalDateTime.parse("2024-09-01T13:31"),
    modifyUserId = "T.SMITH",
    modifyDatetime = LocalDateTime.parse("2024-10-01T13:31"),
  ),
)
fun CorporateOrganisation.withInternetAddress(internetAddress: CorporateInternetAddress): CorporateOrganisation = this.copy(internetAddresses = listOf(internetAddress))

fun corporateWebAddress(): CorporateInternetAddress = CorporateInternetAddress(
  id = 12345,
  internetAddress = "www.boots.gov.uk",
  type = "WEB",
  audit = NomisAudit(
    createUsername = "J.SPEAK",
    createDatetime = LocalDateTime.parse("2024-09-01T13:31"),
    modifyUserId = "T.SMITH",
    modifyDatetime = LocalDateTime.parse("2024-10-01T13:31"),
  ),
)
fun corporateEmail(): CorporateInternetAddress = CorporateInternetAddress(
  id = 12345,
  internetAddress = "jane@test.com",
  type = "EMAIL",
  audit = NomisAudit(
    createUsername = "J.SPEAK",
    createDatetime = LocalDateTime.parse("2024-09-01T13:31"),
    modifyUserId = "T.SMITH",
    modifyDatetime = LocalDateTime.parse("2024-10-01T13:31"),
  ),
)

fun nomisAudit() = NomisAudit(
  createDatetime = LocalDateTime.now(),
  createUsername = "Q1251T",
)
