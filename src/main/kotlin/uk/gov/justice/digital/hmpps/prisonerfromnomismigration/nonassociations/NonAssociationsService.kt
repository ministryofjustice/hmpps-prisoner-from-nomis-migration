package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nonassociations

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nonassociations.model.NonAssociation
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nonassociations.model.UpsertSyncRequest

@Service
class NonAssociationsService(@Qualifier("nonAssociationsApiWebClient") private val webClient: WebClient) {
  suspend fun upsertNonAssociation(upsertRequest: UpsertSyncRequest): NonAssociation =
    webClient.put()
      .uri("/sync/upsert")
      .bodyValue(upsertRequest)
      .retrieve()
      .awaitBody()
}
