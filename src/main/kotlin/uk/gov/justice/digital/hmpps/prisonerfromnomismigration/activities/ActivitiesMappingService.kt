package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities

import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.MigrationMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ActivityMigrationMappingDto

@Service
class ActivitiesMappingService(@Qualifier("mappingApiWebClient") webClient: WebClient) :
  MigrationMapping<ActivityMigrationMappingDto>(domainUrl = "/mapping/activities/migration", webClient) {

  suspend fun findNomisMapping(courseActivityId: Long): ActivityMigrationMappingDto? {
    return webClient.get()
      .uri("/mapping/activities/migration/nomis-course-activity-id/{courseActivityId}", courseActivityId)
      .retrieve()
      .bodyToMono(ActivityMigrationMappingDto::class.java)
      .onErrorResume(WebClientResponseException.NotFound::class.java) {
        Mono.empty()
      }
      .awaitSingleOrNull()
  }
}