package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.court

import kotlinx.coroutines.reactive.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtscheduler.api.SyncApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtscheduler.model.MoveCourtEventRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtscheduler.model.ReferenceId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtscheduler.model.ResyncCourtEvents
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtscheduler.model.ResyncResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtscheduler.model.SyncCourtEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtscheduler.model.SyncCourtEventMovement
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodilessEntityOrLogAndRethrowBadRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrLogAndRethrowBadRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrNullWhenNotFound
import java.util.*

@Service
class CourtSchedulerDpsApiService(
  @Qualifier("courtSchedulerDpsApiWebClient") private val webClient: WebClient,
  @Qualifier("courtSchedulerDpsApiResyncWebClient") private val resyncWebClient: WebClient,
) {

  private val syncApi = SyncApi(webClient)
  private val resyncApi = SyncApi(resyncWebClient)

  suspend fun syncCourtEvent(prisonerNumber: String, request: SyncCourtEvent): ReferenceId = syncApi.prepare(syncApi.syncCourtAppearanceRequestConfig(prisonerNumber, request))
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun deleteCourtEvent(courtAppearanceId: UUID) = syncApi.deleteCourtAppearance(courtAppearanceId).awaitSingle()

  suspend fun syncCourtMovement(prisonerNumber: String, request: SyncCourtEventMovement): ReferenceId = syncApi.prepare(syncApi.syncCourtAppearanceMovementRequestConfig(prisonerNumber, request))
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun deleteCourtMovement(courtMovementId: UUID) = syncApi.deleteCourtAppearanceMovement(courtMovementId).awaitSingle()

  suspend fun resyncPrisoner(personIdentifier: String, request: ResyncCourtEvents) = resyncApi.prepare(resyncApi.resyncCourtAppearancesRequestConfig(personIdentifier, request))
    .retrieve()
    .awaitBodyOrNullWhenNotFound<ResyncResponse>()

  suspend fun moveBooking(request: MoveCourtEventRequest) = syncApi.prepare(syncApi.moveCourtAppearancesRequestConfig(request))
    .retrieve()
    .awaitBodilessEntityOrLogAndRethrowBadRequest()
}
