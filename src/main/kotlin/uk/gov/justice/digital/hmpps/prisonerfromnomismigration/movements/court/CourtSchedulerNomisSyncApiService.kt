package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.court

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisupdate.api.CourtSchedulerResourceApi

@Service
class CourtSchedulerNomisSyncApiService(
  @Qualifier("nomisSyncApiWebClient") private val webClient: WebClient,
) {

  private val courtSchedulerApi = CourtSchedulerResourceApi(webClient)
}
