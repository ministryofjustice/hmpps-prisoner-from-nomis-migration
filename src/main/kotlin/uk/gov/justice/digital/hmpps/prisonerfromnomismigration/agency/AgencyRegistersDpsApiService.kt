package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.agency

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.agencyregisters.api.LegacySyncResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.agencyregisters.model.LegacyAgencyDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.agencyregisters.model.LegacyAgencyResponse

@Service
class AgencyRegistersDpsApiService(
  @Qualifier("agencyApiWebClient") private val webClient: WebClient,
) {
  private val api = LegacySyncResourceApi(webClient)

  suspend fun migrateAgency(agencyId: String, request: LegacyAgencyDto): LegacyAgencyResponse = api.migrateAgency(agencyId, request).awaitSingle()
}
