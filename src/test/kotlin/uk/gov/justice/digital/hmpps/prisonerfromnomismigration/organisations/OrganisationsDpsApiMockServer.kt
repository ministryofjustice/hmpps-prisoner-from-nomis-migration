package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations

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
import org.springframework.stereotype.Component
import org.springframework.test.context.junit.jupiter.SpringExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.OrganisationsDpsApiExtension.Companion.dpsOrganisationsServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.model.IdPair
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.model.MigrateOrganisationRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.model.MigrateOrganisationResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.model.MigratedOrganisationAddress
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.getRequestBodies
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.getRequestBody

class OrganisationsDpsApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val dpsOrganisationsServer = OrganisationsDpsApiMockServer()
    lateinit var objectMapper: ObjectMapper

    @Suppress("unused")
    inline fun <reified T> getRequestBody(pattern: RequestPatternBuilder): T = dpsOrganisationsServer.getRequestBody(pattern, objectMapper)
    inline fun <reified T> getRequestBodies(pattern: RequestPatternBuilder): List<T> = dpsOrganisationsServer.getRequestBodies(pattern, objectMapper)
  }

  override fun beforeAll(context: ExtensionContext) {
    dpsOrganisationsServer.start()
    objectMapper = (SpringExtension.getApplicationContext(context).getBean("jacksonObjectMapper") as ObjectMapper)
  }

  override fun beforeEach(context: ExtensionContext) {
    dpsOrganisationsServer.resetAll()
  }

  override fun afterAll(context: ExtensionContext) {
    dpsOrganisationsServer.stop()
  }
}

@Component
class OrganisationsDpsApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8100

    fun migrateOrganisationRequest() = MigrateOrganisationRequest(
      nomisCorporateId = 123456,
      organisationName = "Test Organisation",
      active = true,
      phoneNumbers = emptyList(),
      addresses = emptyList(),
      emailAddresses = emptyList(),
      webAddresses = emptyList(),
      organisationTypes = emptyList(),
    )

    fun migrateOrganisationResponse(request: MigrateOrganisationRequest = migrateOrganisationRequest()) = MigrateOrganisationResponse(
      organisation = IdPair(elementType = IdPair.ElementType.ORGANISATION, nomisId = request.nomisCorporateId, dpsId = request.nomisCorporateId),
      phoneNumbers = request.phoneNumbers.map { IdPair(elementType = IdPair.ElementType.PHONE, nomisId = it.nomisPhoneId, dpsId = it.nomisPhoneId * 10) },
      addresses = request.addresses.map { MigratedOrganisationAddress(address = IdPair(elementType = IdPair.ElementType.ADDRESS, nomisId = it.nomisAddressId, dpsId = it.nomisAddressId * 10), phoneNumbers = it.phoneNumbers.map { phone -> IdPair(elementType = IdPair.ElementType.PHONE, nomisId = phone.nomisPhoneId, dpsId = phone.nomisPhoneId * 10) }) },
      emailAddresses = request.emailAddresses.map { IdPair(elementType = IdPair.ElementType.EMAIL, nomisId = it.nomisEmailAddressId, dpsId = it.nomisEmailAddressId * 10) },
      webAddresses = request.webAddresses.map { IdPair(elementType = IdPair.ElementType.EMAIL, nomisId = it.nomisWebAddressId, dpsId = it.nomisWebAddressId * 10) },
      // we don't care about mapping types since in NOMIS they have no identity
      organisationTypes = emptyList(),
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

  fun stubMigrateOrganisation(response: MigrateOrganisationResponse = migrateOrganisationResponse()) {
    dpsOrganisationsServer.stubFor(
      post("/migrate/organisation")
        .willReturn(
          aResponse()
            .withStatus(201)
            .withHeader("Content-Type", "application/json")
            .withBody(OrganisationsDpsApiExtension.objectMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubMigrateOrganisation(nomisCorporateId: Long, response: MigrateOrganisationResponse = migrateOrganisationResponse()) {
    dpsOrganisationsServer.stubFor(
      post("/migrate/organisation")
        .willReturn(
          aResponse()
            .withStatus(201)
            .withHeader("Content-Type", "application/json")
            .withBody(OrganisationsDpsApiExtension.objectMapper.writeValueAsString(response)),
        ).withRequestBody(matchingJsonPath("$.nomisCorporateId", equalTo(nomisCorporateId.toString()))),
    )
  }
}
