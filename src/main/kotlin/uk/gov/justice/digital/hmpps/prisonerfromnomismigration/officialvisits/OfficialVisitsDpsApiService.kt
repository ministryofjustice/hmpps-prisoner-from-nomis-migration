package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.api.MigrationApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.MigrateVisitConfigRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.MigrateVisitConfigResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.MigrateVisitRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.MigrateVisitResponse

@Service
class OfficialVisitsDpsApiService(
  @Qualifier("officialVisitsApiWebClient") private val webClient: WebClient,
) {
  private val api = MigrationApi(webClient)

  suspend fun migrateVisitConfiguration(request: MigrateVisitConfigRequest): MigrateVisitConfigResponse = api.migrateVisitConfiguration(request).awaitSingle()
  suspend fun migrateVisit(request: MigrateVisitRequest): MigrateVisitResponse = api.migrateVisit(request).awaitSingle()
}
