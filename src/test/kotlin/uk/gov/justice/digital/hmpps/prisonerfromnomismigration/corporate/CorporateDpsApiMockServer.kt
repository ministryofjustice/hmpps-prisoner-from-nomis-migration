package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.corporate

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.ContactPersonDpsApiExtension.Companion.dpsContactPersonServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.IdPair
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.MigrateOrganisationRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.MigrateOrganisationResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.MigratedOrganisationAddress

@Component
class CorporateDpsApiMockServer(private val objectMapper: ObjectMapper) {
  companion object {
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

  fun stubMigrateOrganisation(response: MigrateOrganisationResponse = migrateOrganisationResponse()) {
    dpsContactPersonServer.stubFor(
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
    dpsContactPersonServer.stubFor(
      post("/migrate/organisation")
        .willReturn(
          aResponse()
            .withStatus(201)
            .withHeader("Content-Type", "application/json")
            .withBody(objectMapper.writeValueAsString(response)),
        ).withRequestBody(matchingJsonPath("$.nomisCorporateId", equalTo(nomisCorporateId.toString()))),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = dpsContactPersonServer.verify(pattern)
  fun resetAll() = dpsContactPersonServer.resetAll()
}
