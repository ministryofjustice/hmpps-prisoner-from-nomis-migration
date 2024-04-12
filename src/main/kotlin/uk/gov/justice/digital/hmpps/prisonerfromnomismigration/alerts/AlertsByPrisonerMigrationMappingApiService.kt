package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts

import kotlinx.coroutines.reactive.awaitFirstOrDefault
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.CreateMappingResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.MigrationMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.AlertMappingDto

@Service
class AlertsByPrisonerMigrationMappingApiService(@Qualifier("mappingApiWebClient") webClient: WebClient) :
  MigrationMapping<List<AlertMappingDto>>(domainUrl = "/mapping/alerts/all", webClient) {
  suspend fun createMapping(
    mappings: List<AlertMappingDto>,
    errorJavaClass: ParameterizedTypeReference<DuplicateErrorResponse<AlertMappingDto>>,
  ): CreateMappingResult<AlertMappingDto> {
    return webClient.post()
      .uri(createMappingUrl())
      .bodyValue(
        mappings,
      )
      .retrieve()
      .bodyToMono(Unit::class.java)
      .map { CreateMappingResult<AlertMappingDto>() }
      .onErrorResume(WebClientResponseException.Conflict::class.java) {
        Mono.just(CreateMappingResult(it.getResponseBodyAs(errorJavaClass)))
      }
      .awaitFirstOrDefault(CreateMappingResult())
  }
}
