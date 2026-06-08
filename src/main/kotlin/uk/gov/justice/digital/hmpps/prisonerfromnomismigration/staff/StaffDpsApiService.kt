package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.staff

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrLogAndRethrowBadRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.staff.model.UserMigrationRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.staff.model.UserMigrationResponse

@Service
class StaffDpsApiService(
  @Qualifier("staffApiWebClient") private val webClient: WebClient,
) {
  suspend fun migrateStaff(userMigrationRequest: UserMigrationRequest): UserMigrationResponse = webClient.post()
    .uri("/prison-users/migrate/staff")
    .bodyValue(userMigrationRequest)
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()
}
