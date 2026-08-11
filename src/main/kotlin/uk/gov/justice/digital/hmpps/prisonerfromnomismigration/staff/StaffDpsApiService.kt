package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.staff

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBodilessEntity
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitOrLogAndRethrowBadRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.staff.api.MigrationResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.staff.api.SyncResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.staff.model.PrisonUserSyncRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.staff.model.UserMigrationRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.staff.model.UserMigrationResponse

@Service
class StaffDpsApiService(
  @Qualifier("staffApiWebClient") private val webClient: WebClient,
) {
  private val migrateApi = MigrationResourceApi(webClient)
  private val syncApi = SyncResourceApi(webClient)

  suspend fun migrateStaff(userMigrationRequest: UserMigrationRequest): UserMigrationResponse = migrateApi.migrateUser(userMigrationRequest)
    .awaitOrLogAndRethrowBadRequest()

  suspend fun syncStaff(nomisStaffId: Long, userMigrationRequest: PrisonUserSyncRequest) {
    syncApi.putPrisonUserForSync(nomisStaffId, userMigrationRequest).awaitOrLogAndRethrowBadRequest()
  }

  suspend fun deleteStaff(nomisStaffId: Long) {
    webClient.delete()
      .uri("/prison-users/staff/{nomisStaffId}", nomisStaffId)
      .retrieve()
      .awaitBodilessEntity()
  }
}
