package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities

import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.MigrationMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ActivityMigrationMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.NomisDpsLocationMapping

@Service
class ActivitiesMappingService(@Qualifier("mappingApiWebClient") webClient: WebClient) : MigrationMapping<ActivityMigrationMappingDto>(domainUrl = "/mapping/activities/migration", webClient) {

  suspend fun findNomisMapping(courseActivityId: Long): ActivityMigrationMappingDto? = webClient.get()
    .uri("/mapping/activities/migration/nomis-course-activity-id/{courseActivityId}", courseActivityId)
    .retrieve()
    .bodyToMono(ActivityMigrationMappingDto::class.java)
    .onErrorResume(WebClientResponseException.NotFound::class.java) {
      Mono.empty()
    }
    .awaitSingleOrNull()

  suspend fun getActivityMigrationDetails(migrationId: String, size: Long = 1): ActivityMigrationDetails = webClient.get()
    .uri {
      it.path("$domainUrl/migration-id/{migrationId}")
        .queryParam("size", size)
        .build(migrationId)
    }
    .retrieve()
    .bodyToMono(ActivityMigrationDetails::class.java)
    .awaitSingle()!!

  suspend fun getDpsLocation(nomisLocationId: Long): NomisDpsLocationMapping = webClient.get()
    .uri("/api/locations/nomis/{nomisLocationId}", nomisLocationId)
    .retrieve()
    .bodyToMono(NomisDpsLocationMapping::class.java)
    .awaitSingle()

  override suspend fun getMigrationCount(migrationId: String): Long = webClient.get()
    .uri {
      it.path("$domainUrl-count/migration-id/{migrationId}")
        .queryParam("includeIgnored", true)
        .build(migrationId)
    }
    .retrieve()
    .bodyToMono<Long>()
    .awaitSingle()
}
