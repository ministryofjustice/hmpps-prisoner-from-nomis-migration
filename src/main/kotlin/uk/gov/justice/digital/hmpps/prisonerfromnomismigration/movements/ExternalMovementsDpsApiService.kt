package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrLogAndRethrowBadRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.ScheduledTemporaryAbsenceRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.SyncResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.TapApplicationRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.TapMovementRequest
import java.util.UUID

@Service
class ExternalMovementsDpsApiService(@Qualifier("extMovementsDpsApiWebClient") private val webClient: WebClient) {
  suspend fun syncTemporaryAbsenceApplication(personIdentifier: String, tapApplicationRequest: TapApplicationRequest): SyncResponse = webClient.put()
    .uri("/sync/temporary-absence-application/{personIdentifier}", personIdentifier)
    .bodyValue(tapApplicationRequest)
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun syncTemporaryAbsenceScheduledMovement(parentId: UUID, scheduledRequest: ScheduledTemporaryAbsenceRequest): SyncResponse = webClient.put()
    .uri("/sync/scheduled-temporary-absence/{parentId}", parentId)
    .bodyValue(scheduledRequest)
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun syncTemporaryAbsenceMovement(personIdentifier: String, movementRequest: TapMovementRequest): SyncResponse = webClient.put()
    .uri("/sync/temporary-absence-movement/{personIdentifier}", personIdentifier)
    .bodyValue(movementRequest)
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()
}
