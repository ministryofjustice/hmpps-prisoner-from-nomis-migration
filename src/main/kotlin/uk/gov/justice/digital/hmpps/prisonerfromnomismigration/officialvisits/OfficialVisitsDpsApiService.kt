package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.api.MigrationApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.api.SynchronisationApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.MigrateVisitConfigRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.MigrateVisitConfigResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.MigrateVisitRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.MigrateVisitResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.SyncCreateTimeSlotRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.SyncTimeSlot

@Service
class OfficialVisitsDpsApiService(
  @Qualifier("officialVisitsApiWebClient") private val webClient: WebClient,
) {
  private val migrationApi = MigrationApi(webClient)
  private val syncApi = SynchronisationApi(webClient)

  suspend fun migrateVisitConfiguration(request: MigrateVisitConfigRequest): MigrateVisitConfigResponse = migrationApi.migrateVisitConfiguration(request).awaitSingle()
  suspend fun createTimeSlot(request: SyncCreateTimeSlotRequest): SyncTimeSlot = syncApi.syncCreateTimeSlot(request).awaitSingle()
  suspend fun migrateVisit(request: MigrateVisitRequest): MigrateVisitResponse = migrationApi.migrateVisit(request).awaitSingle()
}
