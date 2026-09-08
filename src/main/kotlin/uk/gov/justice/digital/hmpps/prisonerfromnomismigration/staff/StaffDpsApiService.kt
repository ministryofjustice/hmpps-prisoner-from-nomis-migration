package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.staff

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBodilessEntity
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitOrLogAndRethrowBadRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.staff.api.SyncResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.staff.model.PrisonUserSyncRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.staff.model.PrisonUserSyncResponse

@Service
class StaffDpsApiService(
  @Qualifier("staffApiWebClient") private val webClient: WebClient,
) {
  private val syncApi = SyncResourceApi(webClient)

  suspend fun syncStaff(nomisStaffId: Long, userSyncRequest: PrisonUserSyncRequest): PrisonUserSyncResponse = syncApi.putPrisonUserForSync(nomisStaffId, userSyncRequest)
    .awaitOrLogAndRethrowBadRequest()

  suspend fun deleteStaff(nomisStaffId: Long) {
    webClient.delete()
      .uri("/prison-users/staff/{nomisStaffId}", nomisStaffId)
      .retrieve()
      .awaitBodilessEntity()
  }
}
