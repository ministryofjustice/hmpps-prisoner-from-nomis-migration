package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.post
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.test.context.junit.jupiter.SpringExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.IdPair
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.MigrateContactRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.MigrateContactResponse

class ContactPersonDpsApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val dpsContactPersonServer = ContactPersonDpsApiMockServer()
    lateinit var objectMapper: ObjectMapper
  }

  override fun beforeAll(context: ExtensionContext) {
    dpsContactPersonServer.start()
    objectMapper = (SpringExtension.getApplicationContext(context).getBean("jacksonObjectMapper") as ObjectMapper)
  }

  override fun beforeEach(context: ExtensionContext) {
    dpsContactPersonServer.resetAll()
  }

  override fun afterAll(context: ExtensionContext) {
    dpsContactPersonServer.stop()
  }
}

class ContactPersonDpsApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8097

    fun migrateContactRequest() = MigrateContactRequest(
      personId = 654321,
      lastName = "KOFI",
      firstName = "KWEKU",
      remitter = false,
      staff = false,
      keepBiometrics = false,
      interpreterRequired = false,
      phoneNumbers = emptyList(),
      addresses = emptyList(),
      emailAddresses = emptyList(),
      identifiers = emptyList(),
    )

    fun migrateContactResponse(request: MigrateContactRequest = migrateContactRequest()) =
      MigrateContactResponse(
        nomisPersonId = request.personId,
        dpsContactId = request.personId * 10,
        lastName = request.lastName,
        dateOfBirth = request.dateOfBirth,
        phoneNumbers = request.phoneNumbers?.map { IdPair(elementType = IdPair.ElementType.PHONE, nomisId = it.phoneId, dpsId = it.phoneId * 10) } ?: emptyList(),
        addresses = request.addresses?.map { IdPair(elementType = IdPair.ElementType.ADDRESS, nomisId = it.addressId, dpsId = it.addressId * 10) } ?: emptyList(),
        emailAddresses = request.emailAddresses?.map { IdPair(elementType = IdPair.ElementType.EMAIL, nomisId = it.emailAddressId, dpsId = it.emailAddressId * 10) } ?: emptyList(),
        identities = request.identifiers?.map { IdPair(elementType = IdPair.ElementType.IDENTITY, nomisId = it.sequence, dpsId = it.sequence * 10) } ?: emptyList(),
        // TODO - not in request yet
        restrictions = emptyList(),
        prisonerContacts = emptyList(),
        prisonerContactRestrictions = emptyList(),
      )
  }

  fun stubMigrateContact(response: MigrateContactResponse = migrateContactResponse()) {
    stubFor(
      post("/migrate/contact")
        .willReturn(
          aResponse()
            .withStatus(201)
            .withHeader("Content-Type", "application/json")
            .withBody(ContactPersonDpsApiExtension.objectMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubMigrateContact(nomisPersonId: Long, response: MigrateContactResponse = migrateContactResponse()) {
    stubFor(
      post("/migrate/contact")
        .willReturn(
          aResponse()
            .withStatus(201)
            .withHeader("Content-Type", "application/json")
            .withBody(ContactPersonDpsApiExtension.objectMapper.writeValueAsString(response)),
        ).withRequestBody(matchingJsonPath("$.personId", equalTo(nomisPersonId.toString()))),
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
