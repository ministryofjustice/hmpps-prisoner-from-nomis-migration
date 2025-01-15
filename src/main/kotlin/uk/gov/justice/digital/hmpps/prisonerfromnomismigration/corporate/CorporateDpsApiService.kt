package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.corporate

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.MigrateOrganisationRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.MigrateOrganisationResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrLogAndRethrowBadRequest

@Service
class CorporateDpsApiService(@Qualifier("corporateDpsApiWebClient") private val webClient: WebClient) {
  suspend fun migrateOrganisation(contact: MigrateOrganisationRequest): MigrateOrganisationResponse = webClient.post()
    .uri("/migrate/organisation")
    .bodyValue(contact)
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()
}
