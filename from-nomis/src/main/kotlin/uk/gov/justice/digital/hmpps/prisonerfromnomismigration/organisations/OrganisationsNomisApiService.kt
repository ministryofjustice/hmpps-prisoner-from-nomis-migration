package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CorporateOrganisation

@Service
class OrganisationsNomisApiService(@Qualifier("nomisApiWebClient") private val webClient: WebClient) {
  suspend fun getCorporateOrganisation(nomisCorporateId: Long): CorporateOrganisation = webClient.get()
    .uri(
      "/corporates/{corporateId}",
      nomisCorporateId,
    )
    .retrieve()
    .awaitBody()
}
