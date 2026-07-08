package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.staff

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBodilessEntity
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrLogAndRethrowBadRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.staff.api.MigrationResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.staff.model.UserMigrationRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.staff.model.UserMigrationResponse

@Service
class StaffDpsApiService(
  @Qualifier("staffApiWebClient") private val webClient: WebClient,
) {
  private val api = MigrationResourceApi(webClient)

  suspend fun migrateStaff(userMigrationRequest: UserMigrationRequest): UserMigrationResponse = api.migrateUser(userMigrationRequest)
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun syncStaff(userMigrationRequest: UserMigrationRequest): UserMigrationResponse = webClient.put()
    .uri("/prison-users/staff")
    .bodyValue(userMigrationRequest)
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun deleteStaff(nomisStaffId: Long) {
    webClient.delete()
      .uri("/prison-users/staff/{nomisStaffId}", nomisStaffId)
      .retrieve()
      .awaitBodilessEntity()
  }
}
