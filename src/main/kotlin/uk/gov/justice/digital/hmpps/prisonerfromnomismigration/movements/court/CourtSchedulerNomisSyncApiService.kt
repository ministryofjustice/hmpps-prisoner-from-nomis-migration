package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.court

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisupdate.api.CourtSchedulerResourceApi
import java.util.UUID

@Service
class CourtSchedulerNomisSyncApiService(
  @Qualifier("nomisSyncApiWebClient") private val webClient: WebClient,
) {

  private val courtSchedulerApi = CourtSchedulerResourceApi(webClient)

  suspend fun recreateCourtScheduleInNomis(prisonerNumber: String, dpsCourtAppearanceId: UUID) = courtSchedulerApi.synchroniseCourtSchedule(prisonerNumber, dpsCourtAppearanceId, recreate = true)
    .awaitSingle()
}
