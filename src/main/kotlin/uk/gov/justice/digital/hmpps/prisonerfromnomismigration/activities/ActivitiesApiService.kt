package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities.model.ActivityMigrateRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities.model.ActivityMigrateResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities.model.AllocationMigrateRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities.model.AllocationMigrateResponse
import java.time.LocalDate

@Service
class ActivitiesApiService(@Qualifier("activitiesApiWebClient") private val webClient: WebClient) {

  suspend fun migrateActivity(migrateRequest: ActivityMigrateRequest): ActivityMigrateResponse = webClient.post()
    .uri("/migrate/activity")
    .bodyValue(migrateRequest)
    .retrieve()
    .bodyToMono<ActivityMigrateResponse>()
    .awaitSingle()

  suspend fun migrateAllocation(migrateRequest: AllocationMigrateRequest): AllocationMigrateResponse = webClient.post()
    .uri("/migrate/allocation")
    .bodyValue(migrateRequest)
    .retrieve()
    .bodyToMono<AllocationMigrateResponse>()
    .awaitSingle()

  suspend fun moveActivityStartDates(prisonCode: String, newStartDate: LocalDate): List<String> = webClient.post()
    .uri {
      it.path("/migrate/{prisonCode}/move-activity-start-dates")
        .queryParam("activityStartDate", newStartDate)
        .build(prisonCode)
    }
    .retrieve()
    .bodyToMono<List<String>>()
    .awaitSingle()
}
