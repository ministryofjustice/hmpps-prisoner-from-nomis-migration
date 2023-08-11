package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities.model.ActivityMigrateRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities.model.ActivityMigrateResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities.model.AllocationMigrateRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities.model.AllocationMigrateResponse

@Service
class ActivitiesApiService(@Qualifier("activitiesApiWebClient") private val webClient: WebClient) {

  suspend fun migrateActivity(migrateRequest: ActivityMigrateRequest): ActivityMigrateResponse =
    webClient.post()
      .uri("/migrate/activity")
      .bodyValue(migrateRequest)
      .retrieve()
      .bodyToMono(ActivityMigrateResponse::class.java)
      .awaitSingle()!!

  suspend fun migrateAllocation(migrateRequest: AllocationMigrateRequest): AllocationMigrateResponse =
    webClient.post()
      .uri("/migrate/allocation")
      .bodyValue(migrateRequest)
      .retrieve()
      .bodyToMono(AllocationMigrateResponse::class.java)
      .awaitSingle()!!
}
