package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

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

    fun migrateCorePersonRequest() = MigrateCorePersonRequest(
      nomisPrisonNumber = "A1234BC",
      activeFlag = true,
      inOutStatus = "OUT",
      offenders = listOf(
        MockCprOffender(
          nomisOffenderId = 1,
          firstName = "KWEKU",
          middleName1 = "FRED",
          middleName2 = "JAMES",
          lastName = "KOFI",
          workingName = true,
          identifiers = emptyList(),
        ),
      ),
      sentenceStartDates = listOf(LocalDate.parse("1980-01-01")),
      phoneNumbers = emptyList(),
      addresses = emptyList(),
      emailAddresses = emptyList(),
      religions = emptyList(),
      nationalities = emptyList(),
      nationalityDetails = emptyList(),
      sexualOrientations = emptyList(),
      disabilities = emptyList(),
      interestsToImmigration = emptyList(),
    )

    fun migrateCorePersonResponse(request: MigrateCorePersonRequest = migrateCorePersonRequest()) = MigrateCorePersonResponse(
      nomisPrisonNumber = request.nomisPrisonNumber,
      cprId = "CPR-" + request.nomisPrisonNumber,
      addressIds = request.addresses?.map { IdPair(nomisId = it.nomisAddressId, cprId = "CPR-" + it.nomisAddressId) },
      phoneIds = request.phoneNumbers?.map { IdPair(nomisId = it.nomisPhoneId, cprId = "CPR-" + it.nomisPhoneId) },
      emailAddressIds = request.emailAddresses?.map { IdPair(nomisId = it.nomisEmailAddressId, cprId = "CPR-" + it.nomisEmailAddressId) },
      // TODO add additional children
      // offenderIds = request.offenders.map { IdPair(nomisId = it.nomisOffenderId, cprId = "CPR-" + it.nomisOffenderId) },
    )
  }

  fun stubMigrateCorePerson(response: MigrateCorePersonResponse = migrateCorePersonResponse()) {
    stubFor(
      post("/syscon-sync")
        .willReturn(
          aResponse()
            .withStatus(201)
            .withHeader("Content-Type", "application/json")
            .withBody(CorePersonCprApiExtension.objectMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubMigrateCorePerson(nomisPrisonNumber: String, response: MigrateCorePersonResponse = migrateCorePersonResponse()) {
    stubFor(
      post("/syscon-sync")
        .withRequestBody(matchingJsonPath("$.nomisPrisonNumber", equalTo(nomisPrisonNumber)))
        .willReturn(
          aResponse()
            .withStatus(201)
            .withHeader("Content-Type", "application/json")
            .withBody(CorePersonCprApiExtension.objectMapper.writeValueAsString(response)),
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
