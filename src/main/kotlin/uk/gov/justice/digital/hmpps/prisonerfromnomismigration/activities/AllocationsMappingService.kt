package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities

import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.MigrationMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.AllocationMigrationMappingDto

@Service
class AllocationsMappingService(@Qualifier("mappingApiWebClient") webClient: WebClient) :
  MigrationMapping<AllocationMigrationMappingDto>(domainUrl = "/mapping/allocations/migration", webClient) {

  suspend fun findNomisMapping(allocationId: Long): AllocationMigrationMappingDto? {
    return webClient.get()
      .uri("/mapping/allocations/migration/nomis-allocation-id/{nomisAllocationId}", allocationId)
      .retrieve()
      .bodyToMono(AllocationMigrationMappingDto::class.java)
      .onErrorResume(WebClientResponseException.NotFound::class.java) {
        Mono.empty()
      }
      .awaitSingleOrNull()
  }
}
