package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrLogAndRethrowBadRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.api.SyncApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.SyncResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.SyncWriteTapAuthorisation
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.SyncWriteTapOccurrence
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.TapMovementRequest
import java.util.UUID

@Service
class ExternalMovementsDpsApiService(@Qualifier("extMovementsDpsApiWebClient") private val webClient: WebClient) {

  private val syncApi = SyncApi(webClient)

  suspend fun syncTapAuthorisation(personIdentifier: String, request: SyncWriteTapAuthorisation): SyncResponse = syncApi.prepare(syncApi.syncTemporaryAbsenceAuthorisationRequestConfig(personIdentifier, request))
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun syncTapOccurrence(authorisationId: UUID, request: SyncWriteTapOccurrence): SyncResponse = syncApi.prepare(syncApi.syncTemporaryAbsenceOccurrenceRequestConfig(authorisationId, request))
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun syncTemporaryAbsenceMovement(personIdentifier: String, movementRequest: TapMovementRequest): SyncResponse = webClient.put()
    .uri("/sync/temporary-absence-movement/{personIdentifier}", personIdentifier)
    .bodyValue(movementRequest)
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()
}
