package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrLogAndRethrowBadRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.model.MigrateOrganisationRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.model.MigrateOrganisationResponse

@Service
class CorporateDpsApiService(@Qualifier("organisationsDpsApiWebClient") private val webClient: WebClient) {
  suspend fun migrateOrganisation(contact: MigrateOrganisationRequest): MigrateOrganisationResponse = webClient.post()
    .uri("/migrate/organisation")
    .bodyValue(contact)
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()
}
