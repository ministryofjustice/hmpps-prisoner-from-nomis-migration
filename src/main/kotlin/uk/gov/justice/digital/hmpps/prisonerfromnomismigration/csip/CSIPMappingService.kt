package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip

import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.MigrationMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CSIPMappingDto

@Service
class CSIPMappingService(@Qualifier("mappingApiWebClient") webClient: WebClient) :
  MigrationMapping<CSIPMappingDto>(domainUrl = "/mapping/csip", webClient) {

  suspend fun findNomisCSIPMapping(nomisCSIPId: Long): CSIPMappingDto? =
    webClient.get()
      .uri("/mapping/csip/nomis-csip-id/{nomisCSIPId}", nomisCSIPId)
      .retrieve()
      .bodyToMono(CSIPMappingDto::class.java)
      .onErrorResume(WebClientResponseException.NotFound::class.java) {
        Mono.empty()
      }
      .awaitSingleOrNull()
}
