package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodilessEntityOrLogAndRethrowBadRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrLogAndRethrowBadRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.api.SyncApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.MigrateTapRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.MigrateTapResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.MoveTemporaryAbsencesRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.SyncResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.SyncWriteTapAuthorisation
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.SyncWriteTapMovement
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.SyncWriteTapOccurrence
import java.util.*

@Service
class ExternalMovementsDpsApiService(
  @Qualifier("extMovementsDpsApiWebClient") private val webClient: WebClient,
  @Qualifier("extMovementsDpsApiResyncWebClient") private val resyncWebClient: WebClient,
) {

  private val syncApi = SyncApi(webClient)
  private val resyncApi = SyncApi(resyncWebClient)

  suspend fun syncTapAuthorisation(personIdentifier: String, request: SyncWriteTapAuthorisation): SyncResponse = syncApi.prepare(syncApi.syncTemporaryAbsenceAuthorisationRequestConfig(personIdentifier, request))
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun deleteTapAuthorisation(authorisationId: UUID) = syncApi.deleteTapAuthorisationById(authorisationId).awaitSingle()

  suspend fun syncTapOccurrence(authorisationId: UUID, request: SyncWriteTapOccurrence): SyncResponse = syncApi.prepare(syncApi.syncTemporaryAbsenceOccurrenceRequestConfig(authorisationId, request))
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun deleteTapOccurrence(occurrenceId: UUID) = syncApi.deleteTapOccurrenceById(occurrenceId).awaitSingle()

  suspend fun syncTapMovement(personIdentifier: String, request: SyncWriteTapMovement): SyncResponse = syncApi.prepare(syncApi.syncTemporaryAbsenceMovementRequestConfig(personIdentifier, request))
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun deleteTapMovement(movementId: UUID) = syncApi.deleteTapMovementById(movementId).awaitSingle()

  // This is the /resync endpoint that we'll call going forward instead of the full migration endpoint. This performs a "patch migration" rather than delete and replace.
  suspend fun resyncPrisonerTaps(personIdentifier: String, request: MigrateTapRequest): MigrateTapResponse = resyncApi.prepare(resyncApi.mergeTemporaryAbsencesRequestConfig(personIdentifier, request))
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun moveBooking(request: MoveTemporaryAbsencesRequest) = syncApi.prepare(syncApi.moveTemporaryAbsencesRequestConfig(request))
    .retrieve()
    .awaitBodilessEntityOrLogAndRethrowBadRequest()
}
