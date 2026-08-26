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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderBelief
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

  fun stubGetAliasesAndIdentifiers(
    prisonNumber: String = "A1234BC",
    aliasesAndIdentifiers: List<CoreOffender>,
    status: HttpStatus = HttpStatus.OK,
    error: ErrorResponse = ErrorResponse(status = status.value()),
  ) {
    nomisApi.stubFor(
      get(urlEqualTo("/core-person/$prisonNumber")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(
            jsonMapper.writeValueAsString(if (status == HttpStatus.OK) corePerson(prisonNumber, aliasesAndIdentifiers) else error),
          ),
      ),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = nomisApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = nomisApi.verify(count, pattern)
}

fun corePerson(prisonNumber: String = "A1234BC", aliasesAndIdentifiers: List<CoreOffender>? = null): CorePerson = CorePerson(
  prisonNumber = prisonNumber,
  activeFlag = true,
  inOutStatus = "OUT",
  offenders = aliasesAndIdentifiers
    ?: listOf(
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
        birthCountry = CodeDescription(code = "UKR", description = "United Kingdom"),
        ethnicity = CodeDescription(code = "A1", description = "A1"),
        sex = CodeDescription(code = "M", description = "Male"),
        nameType = CodeDescription(code = "MAID", description = "Maiden"),
        identifiers = listOf(
          Identifier(
            offenderId = 1,
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
  beliefs = beliefs(),
)

fun beliefs() = listOf(offenderBelief2)
fun multipleBeliefs() = listOf(offenderBelief1, offenderBelief2)

val offenderBelief1 = OffenderBelief(
  beliefId = 1,
  belief = CodeDescription("ZORO", "Zoroastrianism"),
  startDate = LocalDate.parse("2015-08-02"),
  endDate = LocalDate.parse("2016-08-02"),
  audit = NomisAudit(
    createDatetime = LocalDateTime.parse("2016-08-01T10:55:00"),
    createUsername = "KOFEADDY",
    createDisplayName = "KOFE ADDY",
  ),
  changeReason = false,
)

val offenderBelief2 = OffenderBelief(
  beliefId = 2,
  belief = CodeDescription("DRU", "Druid"),
  startDate = LocalDate.parse("2016-08-02"),
  audit = NomisAudit(
    createDatetime = LocalDateTime.parse("2016-08-01T10:55:00"),
    createUsername = "KOFEADDY",
    createDisplayName = "KOFE ADDY",
    modifyDatetime = LocalDateTime.parse("2017-08-01T10:55:00"),
    modifyUserId = "KOFE_MOD",
  ),
  changeReason = true,
  comments = "No longer believes in Zoroastrianism",
)
