package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.test.context.junit.jupiter.SpringExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.getRequestBodies
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.getRequestBody

class CorePersonCprApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val cprCorePersonServer = CorePersonCprApiMockServer()
    lateinit var objectMapper: ObjectMapper

    @Suppress("unused")
    inline fun <reified T> getRequestBody(pattern: RequestPatternBuilder): T = cprCorePersonServer.getRequestBody(pattern, objectMapper)
    inline fun <reified T> getRequestBodies(pattern: RequestPatternBuilder): List<T> = cprCorePersonServer.getRequestBodies(pattern, objectMapper)
  }

  override fun beforeAll(context: ExtensionContext) {
    cprCorePersonServer.start()
    objectMapper = (SpringExtension.getApplicationContext(context).getBean("jacksonObjectMapper") as ObjectMapper)
  }

  override fun beforeEach(context: ExtensionContext) {
    cprCorePersonServer.resetAll()
  }

  override fun afterAll(context: ExtensionContext) {
    cprCorePersonServer.stop()
  }
}

class CorePersonCprApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8099

    fun migrateCorePersonRequest() = Prisoner(
      name = TODO(),
      demographicAttributes = TODO(),
      addresses = TODO(),
      religions = TODO(),
      contactInfo = TODO(),
      aliases = TODO(),
      identifiers = TODO(),
      sentences = TODO(),

//      offenders = listOf(
//        Names(
//          offenderId = "1",
//          title = "MR",
//          firstName = "JOHN",
//          lastName = "SMITH",
//          workingName = true,
//          middleName1 = "FRED",
//          middleName2 = "JAMES",
//          dateOfBirth = LocalDate.parse("1980-01-01"),
//          birthPlace = "LONDON",
//          birthCountry = "ENG",
//          raceCode = "BLACK",
//          sex = Names.Sex.MALE,
//          nameType = Names.NameType.MAIDEN,
//          identifiers = listOf(
//            Identifier(
//              type = "PNC",
//              value = "20/0071818T",
//            ),
//          ),
//        ),
//      ),
//      sentenceStartDates = listOf(LocalDate.parse("1980-01-01")),
//      religion = listOf(Religion()),
//      phoneNumbers = emptyList(),
//      emails = emptyList(),
//      addresses = emptyList(),
//      nationality = "ENG",
//      secondaryNationality = "NOT_ENG",
//      sexualOrientation = "HET",
//      interestToImmigration = true,
//      disability = true,
//      status = "ACTIVE",
    )

//    fun migrateCorePersonResponse(request: Prisoner = migrateCorePersonRequest()) = CreateResponse(
//      // TODO fix when lists correctly set up as nullable
//      addressIds = request.addresses.map { AddressId(prisonAddressId = it.id, cprAddressId = "CPR-" + it.id) },
//      phoneIds = request.phoneNumbers.map { PhoneId(prisonPhoneId = it.phoneId, cprPhoneId = "CPR-" + it.phoneId) },
//      emailIds = request.emails.map { EmailId(prisonEmailId = it.id, cprEmailId = "CPR-" + it.id) },
//      // TODO add additional children
//      // offenderIds = request.offenders.map { IdPair(nomisId = it.nomisOffenderId, cprId = "CPR-" + it.nomisOffenderId) },
//    )
  }

//  fun stubMigrateCorePerson(nomisPrisonNumber: String = "A1234BC", response: CreateResponse = migrateCorePersonResponse()) {
//    stubFor(
//      put("/syscon-sync/$nomisPrisonNumber")
//        .willReturn(
//          aResponse()
//            .withStatus(201)
//            .withHeader("Content-Type", "application/json")
//            .withBody(CorePersonCprApiExtension.objectMapper.writeValueAsString(response)),
//        ),
//    )
//  }

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
}
