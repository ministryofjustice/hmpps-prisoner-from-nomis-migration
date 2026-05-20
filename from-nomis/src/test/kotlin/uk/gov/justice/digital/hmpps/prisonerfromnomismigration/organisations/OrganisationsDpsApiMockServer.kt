package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.delete
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.stereotype.Component
import org.springframework.test.context.junit.jupiter.SpringExtension
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.OrganisationsDpsApiExtension.Companion.dpsOrganisationsServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.OrganisationsDpsApiExtension.Companion.jsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.model.IdPair
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.model.MigrateOrganisationRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.model.MigrateOrganisationResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.model.SyncAddressPhoneResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.model.SyncAddressResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.model.SyncCreateAddressPhoneRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.model.SyncCreateAddressRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.model.SyncCreateEmailRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.model.SyncCreateOrganisationRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.model.SyncCreatePhoneRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.model.SyncCreateWebRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.model.SyncEmailResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.model.SyncOrganisationResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.model.SyncOrganisationType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.model.SyncPhoneResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.model.SyncUpdateAddressPhoneRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.model.SyncUpdateAddressRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.model.SyncUpdateEmailRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.model.SyncUpdateOrganisationRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.model.SyncUpdatePhoneRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.model.SyncUpdateTypesRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.model.SyncUpdateWebRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.model.SyncWebResponse
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
    lateinit var jsonMapper: JsonMapper

    @Suppress("unused")
    inline fun <reified T> getRequestBody(pattern: RequestPatternBuilder): T = dpsOrganisationsServer.getRequestBody(pattern, jsonMapper)
    inline fun <reified T> getRequestBodies(pattern: RequestPatternBuilder): List<T> = dpsOrganisationsServer.getRequestBodies(pattern, jsonMapper)
  }

  override fun beforeAll(context: ExtensionContext) {
    dpsOrganisationsServer.start()
    jsonMapper = (SpringExtension.getApplicationContext(context).getBean("jacksonJsonMapper") as JsonMapper)
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
      phoneNumbers = emptyList(),
      addresses = emptyList(),
      emailAddresses = emptyList(),
      webAddresses = emptyList(),
      organisationTypes = emptyList(),
    )

    fun syncCreateOrganisationRequest() = SyncCreateOrganisationRequest(
      organisationId = 123456,
      organisationName = "Test Organisation",
      active = true,
      createdBy = "JANE.SAM",
      createdTime = LocalDateTime.parse("2024-01-01T12:13"),
    )

    fun syncOrganisationResponse() = SyncOrganisationResponse(
      organisationId = 123456,
      active = true,
      createdBy = "JANE.SAM",
      createdTime = LocalDateTime.parse("2024-01-01T12:13"),
    )

    fun syncUpdateOrganisationRequest() = SyncUpdateOrganisationRequest(
      organisationId = 123456,
      organisationName = "Test Organisation",
      active = true,
      updatedBy = "JANE.SAM",
      updatedTime = LocalDateTime.parse("2024-01-01T12:13"),
    )

    fun syncCreateAddressRequest() = SyncCreateAddressRequest(
      organisationId = 1234567,
      addressType = "BUS",
      primaryAddress = true,
      createdBy = "JANE.SAM",
      createdTime = LocalDateTime.parse("2024-01-01T12:13"),
      mailAddress = false,
      serviceAddress = false,
      noFixedAddress = false,
    )

    fun syncAddressResponse() = SyncAddressResponse(
      organisationAddressId = 123456,
      organisationId = 1234567,
      primaryAddress = false,
      mailAddress = false,
      serviceAddress = false,
      noFixedAddress = false,
      createdBy = "JANE.SAM",
      createdTime = LocalDateTime.parse("2024-01-01T12:13"),
    )

    fun syncUpdateAddressRequest() = SyncUpdateAddressRequest(
      addressType = "MOB",
      primaryAddress = true,
      updatedBy = "JANE.SAM",
      updatedTime = LocalDateTime.parse("2024-01-01T12:13"),
      organisationId = 1234567,
      mailAddress = false,
      serviceAddress = false,
      noFixedAddress = false,
    )

    fun syncCreatePhoneRequest() = SyncCreatePhoneRequest(
      organisationId = 1234567,
      phoneType = "MOB",
      phoneNumber = "0114 555 5555",
      createdBy = "JANE.SAM",
      createdTime = LocalDateTime.parse("2024-01-01T12:13"),
    )

    fun syncPhoneResponse() = SyncPhoneResponse(
      organisationPhoneId = 123456,
      organisationId = 1234567,
      phoneType = "MOB",
      phoneNumber = "0114 555 5555",
      createdBy = "JANE.SAM",
      createdTime = LocalDateTime.parse("2024-01-01T12:13"),
    )

    fun syncUpdatePhoneRequest() = SyncUpdatePhoneRequest(
      organisationId = 1234567,
      phoneType = "MOB",
      phoneNumber = "0114 555 5555",
      updatedBy = "JANE.SAM",
      updatedTime = LocalDateTime.parse("2024-01-01T12:13"),
    )

    fun syncCreateOrganisationAddressPhoneRequest() = SyncCreateAddressPhoneRequest(
      organisationAddressId = 345678,
      phoneType = "MOB",
      phoneNumber = "0114 555 5555",
      createdBy = "JANE.SAM",
      createdTime = LocalDateTime.parse("2024-01-01T12:13"),
    )
    fun syncCreateAddressPhoneResponse() = SyncAddressPhoneResponse(
      organisationId = 1234567,
      organisationAddressPhoneId = 123456,
      organisationPhoneId = 123456,
      organisationAddressId = 123456,
      phoneType = "MOB",
      phoneNumber = "0114 555 5555",
      createdBy = "JANE.SAM",
      createdTime = LocalDateTime.parse("2024-01-01T12:13"),
    )
    fun syncUpdateAddressPhoneRequest() = SyncUpdateAddressPhoneRequest(
      phoneType = "MOB",
      phoneNumber = "0114 555 5555",
      updatedBy = "JANE.SAM",
      updatedTime = LocalDateTime.parse("2024-01-01T12:13"),
    )
    fun syncCreateWebRequest() = SyncCreateWebRequest(
      organisationId = 1234567,
      webAddress = "www.test.com",
      createdBy = "JANE.SAM",
      createdTime = LocalDateTime.parse("2024-01-01T12:13"),
    )
    fun syncWebResponse() = SyncWebResponse(
      organisationWebAddressId = 123456,
      organisationId = 1234567,
      webAddress = "www.test.com",
      createdBy = "JANE.SAM",
      createdTime = LocalDateTime.parse("2024-01-01T12:13"),
    )
    fun syncUpdateWebRequest() = SyncUpdateWebRequest(
      organisationId = 1234567,
      webAddress = "www.test.com",
      updatedBy = "JANE.SAM",
      updatedTime = LocalDateTime.parse("2024-01-01T12:13"),
    )
    fun syncCreateEmailRequest() = SyncCreateEmailRequest(
      organisationId = 1234567,
      emailAddress = "jane@test.com",
      createdBy = "JANE.SAM",
      createdTime = LocalDateTime.parse("2024-01-01T12:13"),
    )
    fun syncEmailResponse() = SyncEmailResponse(
      organisationEmailId = 123456,
      organisationId = 1234567,
      emailAddress = "jane@test.com",
      createdBy = "JANE.SAM",
      createdTime = LocalDateTime.parse("2024-01-01T12:13"),
    )
    fun syncUpdateEmailRequest() = SyncUpdateEmailRequest(
      organisationId = 1234567,
      emailAddress = "jane@test.com",
      updatedBy = "JANE.SAM",
      updatedTime = LocalDateTime.parse("2024-01-01T12:13"),
    )
    fun syncUpdateTypesRequest() = SyncUpdateTypesRequest(
      organisationId = 1234567,
      types = listOf(
        SyncOrganisationType(
          type = "TEA",
          createdBy = "JANE.SAM",
          createdTime = LocalDateTime.parse("2024-01-01T12:13"),
          updatedBy = "JANE.SAM",
          updatedTime = LocalDateTime.parse("2024-01-01T12:13"),
        ),
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

  fun stubMigrateOrganisation(response: MigrateOrganisationResponse = migrateOrganisationResponse()) {
    dpsOrganisationsServer.stubFor(
      post("/migrate/organisation")
        .willReturn(
          aResponse()
            .withStatus(201)
            .withHeader("Content-Type", "application/json")
            .withBody(jsonMapper.writeValueAsString(response)),
        ),
    )
  }
  fun stubCreateOrganisation(response: SyncOrganisationResponse = syncOrganisationResponse()) {
    dpsOrganisationsServer.stubFor(
      post("/sync/organisation")
        .willReturn(
          aResponse()
            .withStatus(201)
            .withHeader("Content-Type", "application/json")
            .withBody(jsonMapper.writeValueAsString(response)),
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
  fun stubCreateOrganisationAddress(response: SyncAddressResponse = syncAddressResponse()) {
    dpsOrganisationsServer.stubFor(
      post("/sync/organisation-address")
        .willReturn(
          aResponse()
            .withStatus(201)
            .withHeader("Content-Type", "application/json")
            .withBody(jsonMapper.writeValueAsString(response)),
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

  fun stubCreateOrganisationPhone(response: SyncPhoneResponse = syncPhoneResponse()) {
    dpsOrganisationsServer.stubFor(
      post("/sync/organisation-phone")
        .willReturn(
          aResponse()
            .withStatus(201)
            .withHeader("Content-Type", "application/json")
            .withBody(jsonMapper.writeValueAsString(response)),
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

  fun stubCreateOrganisationAddressPhone(response: SyncAddressPhoneResponse = syncCreateAddressPhoneResponse()) {
    dpsOrganisationsServer.stubFor(
      post("/sync/organisation-address-phone")
        .willReturn(
          aResponse()
            .withStatus(201)
            .withHeader("Content-Type", "application/json")
            .withBody(jsonMapper.writeValueAsString(response)),
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
  fun stubCreateOrganisationWebAddress(response: SyncWebResponse = syncWebResponse()) {
    dpsOrganisationsServer.stubFor(
      post("/sync/organisation-web")
        .willReturn(
          aResponse()
            .withStatus(201)
            .withHeader("Content-Type", "application/json")
            .withBody(jsonMapper.writeValueAsString(response)),
        ),
    )
  }
  fun stubUpdateOrganisationWebAddress(organisationWebAddressId: Long) {
    dpsOrganisationsServer.stubFor(
      put("/sync/organisation-web/$organisationWebAddressId")
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json"),
        ),
    )
  }

  fun stubDeleteOrganisationWebAddress(organisationWebAddressId: Long) {
    dpsOrganisationsServer.stubFor(
      delete("/sync/organisation-web/$organisationWebAddressId")
        .willReturn(
          aResponse()
            .withStatus(204)
            .withHeader("Content-Type", "application/json"),
        ),
    )
  }
  fun stubCreateOrganisationEmail(response: SyncEmailResponse = syncEmailResponse()) {
    dpsOrganisationsServer.stubFor(
      post("/sync/organisation-email")
        .willReturn(
          aResponse()
            .withStatus(201)
            .withHeader("Content-Type", "application/json")
            .withBody(jsonMapper.writeValueAsString(response)),
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

  fun stubUpdateOrganisationTypes(organisationId: Long) {
    dpsOrganisationsServer.stubFor(
      put("/sync/organisation-types/$organisationId")
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json"),
        ),
    )
  }
}
