package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.http.HttpStatus
import org.springframework.test.context.junit.jupiter.SpringExtension
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.DemographicAttributes
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.Identifier
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.Identifier.Type
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.Name
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.Sentence
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.OrganisationsDpsApiExtension.Companion.jsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.getRequestBodies
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.getRequestBody
import java.time.LocalDate

class CorePersonCprApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val cprCorePersonServer = CorePersonCprApiMockServer()
    lateinit var jsonMapper: JsonMapper

    @Suppress("unused")
    inline fun <reified T> getRequestBody(pattern: RequestPatternBuilder): T = cprCorePersonServer.getRequestBody(pattern, jsonMapper)
    inline fun <reified T> getRequestBodies(pattern: RequestPatternBuilder): List<T> = cprCorePersonServer.getRequestBodies(pattern, jsonMapper)
  }

  override fun beforeAll(context: ExtensionContext) {
    cprCorePersonServer.start()
    jsonMapper = (SpringExtension.getApplicationContext(context).getBean("jacksonJsonMapper") as JsonMapper)
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
      name = Name(
        titleCode = "MR",
        firstName = "JOHN",
        lastName = "SMITH",
        middleNames = "FRED JAMES",
      ),
      demographicAttributes = DemographicAttributes(
        dateOfBirth = LocalDate.parse("1980-01-01"),
        birthPlace = "LONDON",
        birthCountryCode = "ENG",
        ethnicityCode = "BLACK",
        sexCode = "M",
        sexualOrientation = "HET",
        disability = true,
        interestToImmigration = true,
        religionCode = "REL",
        nationalityCode = "ENG",
        nationalityNote = "NOT_ENG",
      ),
      identifiers = listOf(
        Identifier(
          type = Type.PNC,
          value = "20/0071818T",
        ),
      ),
      aliases = listOf(),
      addresses = listOf(),
      contacts = listOf(),
      sentences = listOf(
        Sentence(LocalDate.parse("1980-01-01")),
      ),
    )

    fun migrateCorePersonResponse(request: Prisoner = migrateCorePersonRequest()) = "OK"
  }

  fun stubMigrateCorePerson(nomisPrisonNumber: String = "A1234BC", response: String = migrateCorePersonResponse()) {
    stubFor(
      put("/syscon-sync/$nomisPrisonNumber")
        .willReturn(
          aResponse()
            .withStatus(201)
            .withHeader("Content-Type", "application/json")
            .withBody(CorePersonCprApiExtension.jsonMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubSyncCreateOffenderBelief(
    prisonNumber: String = "A1234BC",
    status: HttpStatus = HttpStatus.OK,
    error: ErrorResponse = ErrorResponse(status = status.value()),
  ) {
    stubFor(
      post("/syscon-sync/religion/$prisonNumber")
        .willReturn(
          aResponse()
            .withStatus(status.value())
            .withHeader("Content-Type", "application/json")
            .withBody(jsonMapper.writeValueAsString(if (status == HttpStatus.OK) status else error)),
        ),
    )
  }

  fun stubSyncCreateSexualOrientation(prisonNumber: String, status: Int = 201) {
    stubFor(
      post("/syscon-sync/sexual-orientation/$prisonNumber").willReturn(
        aResponse()
          .withStatus(status)
          .withHeader("Content-Type", "application/json")
          .withBody("success"),
      ),
    )
  }

  fun stubSyncCreateDisability(prisonNumber: String, status: Int = 201) {
    stubFor(
      post("/syscon-sync/disability-status/$prisonNumber").willReturn(
        aResponse()
          .withStatus(status)
          .withHeader("Content-Type", "application/json")
          .withBody("success"),
      ),
    )
  }

  fun stubSyncCreateImmigration(prisonNumber: String, status: Int = 201) {
    stubFor(
      post("/syscon-sync/immigration-status/$prisonNumber").willReturn(
        aResponse()
          .withStatus(status)
          .withHeader("Content-Type", "application/json")
          .withBody("success"),
      ),
    )
  }

  fun stubSyncCreateNationality(prisonNumber: String, status: Int = 201) {
    stubFor(
      post("/syscon-sync/nationality/$prisonNumber").willReturn(
        aResponse()
          .withStatus(status)
          .withHeader("Content-Type", "application/json")
          .withBody("success"),
      ),
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
}
