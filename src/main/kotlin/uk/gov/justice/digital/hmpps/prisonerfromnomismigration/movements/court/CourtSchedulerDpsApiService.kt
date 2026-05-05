package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.court

import kotlinx.coroutines.reactive.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtscheduler.api.SyncApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtscheduler.model.ReferenceId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtscheduler.model.SyncCourtEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtscheduler.model.SyncCourtEventMovement
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrLogAndRethrowBadRequest
import java.util.UUID

@Service
class CourtSchedulerDpsApiService(
  @Qualifier("courtSchedulerDpsApiWebClient") private val webClient: WebClient,
) {

  private val syncApi = SyncApi(webClient)

  suspend fun syncCourtEvent(prisonerNumber: String, request: SyncCourtEvent): ReferenceId = syncApi.prepare(syncApi.syncCourtAppearanceRequestConfig(prisonerNumber, request))
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun deleteCourtEvent(courtAppearanceId: UUID) = syncApi.deleteCourtAppearance(courtAppearanceId).awaitSingle()

  suspend fun syncCourtMovement(prisonerNumber: String, request: SyncCourtEventMovement): ReferenceId = syncApi.prepare(syncApi.syncCourtAppearanceMovementRequestConfig(prisonerNumber, request))
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun deleteCourtMovement(courtMovementId: UUID) = syncApi.deleteCourtAppearanceMovement(courtMovementId).awaitSingle()
}
