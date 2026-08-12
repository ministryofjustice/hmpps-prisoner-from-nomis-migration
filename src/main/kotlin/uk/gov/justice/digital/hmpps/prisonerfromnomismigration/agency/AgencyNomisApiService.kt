package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.agency

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.api.AgencyResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.AgencyResponse

@Service
class AgencyNomisApiService(@Qualifier("nomisApiWebClient") private val webClient: WebClient) {
  private val api = AgencyResourceApi(webClient)

  suspend fun getAgency(
    agencyId: String,
  ): AgencyResponse = api.getAgency(
    agencyId = agencyId,
  ).awaitSingle()
}
