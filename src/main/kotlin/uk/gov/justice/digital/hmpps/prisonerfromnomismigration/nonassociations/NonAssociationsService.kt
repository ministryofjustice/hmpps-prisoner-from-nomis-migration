package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nonassociations

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nonassociations.model.CreateSyncRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nonassociations.model.NonAssociation

@Service
class NonAssociationsService(@Qualifier("nonAssociationsApiWebClient") private val webClient: WebClient) {
  suspend fun createNonAssociation(nonAssociationSyncRequest: CreateSyncRequest): NonAssociation =
    webClient.post()
      .uri("/sync")
      .bodyValue(nonAssociationSyncRequest)
      .retrieve()
      .awaitBody()
}
