package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.court

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtscheduler.api.SyncApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtscheduler.model.ReferenceId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtscheduler.model.SyncCourtEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrLogAndRethrowBadRequest

@Service
class CourtSchedulerDpsApiService(
  @Qualifier("courtSchedulerDpsApiWebClient") private val webClient: WebClient,
) {

  private val syncApi = SyncApi(webClient)

  suspend fun syncCourtEvent(prisonerNumber: String, request: SyncCourtEvent): ReferenceId = syncApi.prepare(syncApi.syncCourtAppearanceRequestConfig(prisonerNumber, request))
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()
}
