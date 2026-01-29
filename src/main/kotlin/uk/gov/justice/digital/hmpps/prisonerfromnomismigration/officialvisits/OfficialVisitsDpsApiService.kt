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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.SyncCreateVisitSlotRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.SyncTimeSlot
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.SyncUpdateTimeSlotRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.SyncUpdateVisitSlotRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.SyncVisitSlot

@Service
class OfficialVisitsDpsApiService(
  @Qualifier("officialVisitsApiWebClient") private val webClient: WebClient,
) {
  private val migrationApi = MigrationApi(webClient)
  private val syncApi = SynchronisationApi(webClient)

  suspend fun migrateVisitConfiguration(request: MigrateVisitConfigRequest): MigrateVisitConfigResponse = migrationApi.migrateVisitConfiguration(request).awaitSingle()
  suspend fun createTimeSlot(request: SyncCreateTimeSlotRequest): SyncTimeSlot = syncApi.syncCreateTimeSlot(request).awaitSingle()
  suspend fun updateTimeSlot(prisonTimeSlotId: Long, request: SyncUpdateTimeSlotRequest): SyncTimeSlot = syncApi.syncUpdateTimeSlot(prisonTimeSlotId, request).awaitSingle()
  suspend fun migrateVisit(request: MigrateVisitRequest): MigrateVisitResponse = migrationApi.migrateVisit(request).awaitSingle()
  suspend fun createVisitSlot(request: SyncCreateVisitSlotRequest): SyncVisitSlot = syncApi.syncCreateVisitSlot(request).awaitSingle()
  suspend fun updateVisitSlot(prisonVisitSlotId: Long, request: SyncUpdateVisitSlotRequest): SyncVisitSlot = syncApi.syncUpdateVisitSlot(prisonVisitSlotId, request).awaitSingle()
}
