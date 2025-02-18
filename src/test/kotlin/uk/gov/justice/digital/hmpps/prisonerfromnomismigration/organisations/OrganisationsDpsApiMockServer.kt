package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.delete
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.stereotype.Component
import org.springframework.test.context.junit.jupiter.SpringExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.OrganisationsDpsApiExtension.Companion.dpsOrganisationsServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.OrganisationsDpsApiExtension.Companion.objectMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.model.IdPair
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.model.MigrateOrganisationRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.model.MigrateOrganisationResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.model.MigratedOrganisationAddress
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.getRequestBodies
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.getRequestBody
import java.time.LocalDateTime

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

    fun syncCreateOrganisationRequest() = SyncCreateOrganisationRequest(
      organisationId = 123456,
      organisationName = "Test Organisation",
      active = true,
    )

    fun syncCreateOrganisationResponse() = SyncCreateOrganisationResponse(
      organisationId = 123456,
    )

    fun syncUpdateOrganisationRequest() = SyncUpdateOrganisationRequest(
      organisationName = "Test Organisation",
      active = true,
    )

    fun syncCreateOrganisationAddressRequest() = SyncCreateOrganisationAddressRequest(
      organisationId = 1234567,
      addressType = "BUS",
      primaryAddress = true,
      createdBy = "JANE.SAM",
      createdTime = LocalDateTime.parse("2024-01-01T12:13"),
    )

    fun syncCreateOrganisationAddressResponse() = SyncCreateOrganisationAddressResponse(
      organisationAddressId = 123456,
    )

    fun syncUpdateOrganisationAddressRequest() = SyncUpdateOrganisationAddressRequest(
      addressType = "MOB",
      primaryAddress = true,
      updatedBy = "JANE.SAM",
      updatedTime = LocalDateTime.parse("2024-01-01T12:13"),
      verified = false,
    )

    fun syncCreateOrganisationPhoneRequest() = SyncCreateOrganisationPhoneRequest(
      organisationId = 1234567,
      phoneType = "MOB",
      phoneNumber = "0114 555 5555",
      createdBy = "JANE.SAM",
      createdTime = LocalDateTime.parse("2024-01-01T12:13"),
    )

    fun syncCreateOrganisationPhoneResponse() = SyncCreateOrganisationPhoneResponse(
      organisationPhoneId = 123456,
    )

    fun syncUpdateOrganisationPhoneRequest() = SyncUpdateOrganisationPhoneRequest(
      phoneType = "MOB",
      phoneNumber = "0114 555 5555",
      updatedBy = "JANE.SAM",
      updatedTime = LocalDateTime.parse("2024-01-01T12:13"),
    )

    fun syncCreateOrganisationAddressPhoneRequest() = SyncCreateOrganisationAddressPhoneRequest(
      organisationId = 1234567,
      organisationAddressId = 345678,
      phoneType = "MOB",
      phoneNumber = "0114 555 5555",
      createdBy = "JANE.SAM",
      createdTime = LocalDateTime.parse("2024-01-01T12:13"),
    )
    fun syncCreateOrganisationAddressPhoneResponse() = SyncCreateOrganisationAddressPhoneResponse(
      organisationAddressPhoneId = 123456,
    )
    fun syncUpdateOrganisationAddressPhoneRequest() = SyncUpdateOrganisationAddressPhoneRequest(
      phoneType = "MOB",
      phoneNumber = "0114 555 5555",
      updatedBy = "JANE.SAM",
      updatedTime = LocalDateTime.parse("2024-01-01T12:13"),
    )
    fun syncCreateOrganisationWebAddressRequest() = SyncCreateOrganisationWebAddressRequest(
      organisationId = 1234567,
      webAddress = "www.test.com",
      createdBy = "JANE.SAM",
      createdTime = LocalDateTime.parse("2024-01-01T12:13"),
    )
    fun syncCreateOrganisationWebAddressResponse() = SyncCreateOrganisationWebAddressResponse(
      organisationWebAddressId = 123456,
    )
    fun syncUpdateOrganisationWebAddressRequest() = SyncUpdateOrganisationWebAddressRequest(
      webAddress = "www.test.com",
      updatedBy = "JANE.SAM",
      updatedTime = LocalDateTime.parse("2024-01-01T12:13"),
    )
    fun syncCreateOrganisationEmailRequest() = SyncCreateOrganisationEmailRequest(
      organisationId = 1234567,
      emailAddress = "jane@test.com",
      createdBy = "JANE.SAM",
      createdTime = LocalDateTime.parse("2024-01-01T12:13"),
    )
    fun syncCreateOrganisationEmailResponse() = SyncCreateOrganisationEmailResponse(
      organisationEmailId = 123456,
    )
    fun syncUpdateOrganisationEmailRequest() = SyncUpdateOrganisationEmailRequest(
      emailAddress = "jane@test.com",
      updatedBy = "JANE.SAM",
      updatedTime = LocalDateTime.parse("2024-01-01T12:13"),
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
            .withBody(objectMapper.writeValueAsString(response)),
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
            .withBody(objectMapper.writeValueAsString(response)),
        ).withRequestBody(matchingJsonPath("$.nomisCorporateId", equalTo(nomisCorporateId.toString()))),
    )
  }

  fun stubCreateOrganisation(response: SyncCreateOrganisationResponse = syncCreateOrganisationResponse()) {
    dpsOrganisationsServer.stubFor(
      post("/sync/organisation")
        .willReturn(
          aResponse()
            .withStatus(201)
            .withHeader("Content-Type", "application/json")
            .withBody(objectMapper.writeValueAsString(response)),
        ),
    )
  }
  fun stubUpdateOrganisation(organisationId: Long) {
    dpsOrganisationsServer.stubFor(
      put("/sync/organisation/$organisationId")
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json"),
        ),
    )
  }

  fun stubDeleteOrganisation(organisationId: Long) {
    dpsOrganisationsServer.stubFor(
      delete("/sync/organisation/$organisationId")
        .willReturn(
          aResponse()
            .withStatus(204)
            .withHeader("Content-Type", "application/json"),
        ),
    )
  }
  fun stubCreateOrganisationAddress(response: SyncCreateOrganisationAddressResponse = syncCreateOrganisationAddressResponse()) {
    dpsOrganisationsServer.stubFor(
      post("/sync/organisation-address")
        .willReturn(
          aResponse()
            .withStatus(201)
            .withHeader("Content-Type", "application/json")
            .withBody(objectMapper.writeValueAsString(response)),
        ),
    )
  }
  fun stubUpdateOrganisationAddress(organisationAddressId: Long) {
    dpsOrganisationsServer.stubFor(
      put("/sync/organisation-address/$organisationAddressId")
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json"),
        ),
    )
  }

  fun stubDeleteOrganisationAddress(organisationAddressId: Long) {
    dpsOrganisationsServer.stubFor(
      delete("/sync/organisation-address/$organisationAddressId")
        .willReturn(
          aResponse()
            .withStatus(204)
            .withHeader("Content-Type", "application/json"),
        ),
    )
  }

  fun stubCreateOrganisationPhone(response: SyncCreateOrganisationPhoneResponse = syncCreateOrganisationPhoneResponse()) {
    dpsOrganisationsServer.stubFor(
      post("/sync/organisation-phone")
        .willReturn(
          aResponse()
            .withStatus(201)
            .withHeader("Content-Type", "application/json")
            .withBody(objectMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubUpdateOrganisationPhone(organisationPhoneId: Long) {
    dpsOrganisationsServer.stubFor(
      put("/sync/organisation-phone/$organisationPhoneId")
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json"),
        ),
    )
  }

  fun stubDeleteOrganisationPhone(organisationPhoneId: Long) {
    dpsOrganisationsServer.stubFor(
      delete("/sync/organisation-phone/$organisationPhoneId")
        .willReturn(
          aResponse()
            .withStatus(204)
            .withHeader("Content-Type", "application/json"),
        ),
    )
  }

  fun stubCreateOrganisationAddressPhone(response: SyncCreateOrganisationAddressPhoneResponse = syncCreateOrganisationAddressPhoneResponse()) {
    dpsOrganisationsServer.stubFor(
      post("/sync/organisation-address-phone")
        .willReturn(
          aResponse()
            .withStatus(201)
            .withHeader("Content-Type", "application/json")
            .withBody(objectMapper.writeValueAsString(response)),
        ),
    )
  }
  fun stubUpdateOrganisationAddressPhone(organisationAddressPhoneId: Long) {
    dpsOrganisationsServer.stubFor(
      put("/sync/organisation-address-phone/$organisationAddressPhoneId")
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json"),
        ),
    )
  }

  fun stubDeleteOrganisationAddressPhone(organisationAddressPhoneId: Long) {
    dpsOrganisationsServer.stubFor(
      delete("/sync/organisation-address-phone/$organisationAddressPhoneId")
        .willReturn(
          aResponse()
            .withStatus(204)
            .withHeader("Content-Type", "application/json"),
        ),
    )
  }
  fun stubCreateOrganisationWebAddress(response: SyncCreateOrganisationWebAddressResponse = syncCreateOrganisationWebAddressResponse()) {
    dpsOrganisationsServer.stubFor(
      post("/sync/organisation-web-address")
        .willReturn(
          aResponse()
            .withStatus(201)
            .withHeader("Content-Type", "application/json")
            .withBody(objectMapper.writeValueAsString(response)),
        ),
    )
  }
  fun stubUpdateOrganisationWebAddress(organisationWebAddressId: Long) {
    dpsOrganisationsServer.stubFor(
      put("/sync/organisation-web-address/$organisationWebAddressId")
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json"),
        ),
    )
  }

  fun stubDeleteOrganisationWebAddress(organisationWebAddressId: Long) {
    dpsOrganisationsServer.stubFor(
      delete("/sync/organisation-web-address/$organisationWebAddressId")
        .willReturn(
          aResponse()
            .withStatus(204)
            .withHeader("Content-Type", "application/json"),
        ),
    )
  }
  fun stubCreateOrganisationEmail(response: SyncCreateOrganisationEmailResponse = syncCreateOrganisationEmailResponse()) {
    dpsOrganisationsServer.stubFor(
      post("/sync/organisation-email")
        .willReturn(
          aResponse()
            .withStatus(201)
            .withHeader("Content-Type", "application/json")
            .withBody(objectMapper.writeValueAsString(response)),
        ),
    )
  }
  fun stubUpdateOrganisationEmail(organisationEmailId: Long) {
    dpsOrganisationsServer.stubFor(
      put("/sync/organisation-email/$organisationEmailId")
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json"),
        ),
    )
  }

  fun stubDeleteOrganisationEmail(organisationEmailId: Long) {
    dpsOrganisationsServer.stubFor(
      delete("/sync/organisation-email/$organisationEmailId")
        .willReturn(
          aResponse()
            .withStatus(204)
            .withHeader("Content-Type", "application/json"),
        ),
    )
  }
}
