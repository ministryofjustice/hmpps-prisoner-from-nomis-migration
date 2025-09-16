package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrLogAndRethrowBadRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.SyncResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.TapApplicationRequest

@Service
class ExternalMovementsDpsApiService(@Qualifier("extMovementsDpsApiWebClient") private val webClient: WebClient) {
  suspend fun syncTemporaryAbsenceApplication(personIdentifier: String, tapApplicationRequest: TapApplicationRequest): SyncResponse = webClient.put()
    .uri("/sync/temporary-absence-application/{personIdentifier}", personIdentifier)
    .bodyValue(tapApplicationRequest)
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()
}
