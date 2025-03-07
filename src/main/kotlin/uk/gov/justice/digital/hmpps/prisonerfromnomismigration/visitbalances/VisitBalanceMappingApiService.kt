package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visitbalances

import kotlinx.coroutines.reactive.awaitFirstOrDefault
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.awaitBody
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrNullWhenNotFound
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.CreateMappingResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.MigrationMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.VisitBalanceMappingDto

@Service
class VisitBalanceMappingApiService(@Qualifier("mappingApiWebClient") webClient: WebClient) : MigrationMapping<VisitBalanceMappingDto>(domainUrl = "/mapping/visit-balance", webClient) {

  suspend fun createMapping(mappings: VisitBalanceMappingDto): CreateMappingResult<VisitBalanceMappingDto> = webClient.post()
    .uri("/mapping/visit-balance/migrate")
    .bodyValue(mappings)
    .retrieve()
    .bodyToMono(Unit::class.java)
    .map { CreateMappingResult<VisitBalanceMappingDto>() }
    .onErrorResume(WebClientResponseException.Conflict::class.java) {
      Mono.just(CreateMappingResult(it.getResponseBodyAs(object : ParameterizedTypeReference<DuplicateErrorResponse<VisitBalanceMappingDto>>() {})))
    }
    .awaitFirstOrDefault(CreateMappingResult())

  suspend fun getByNomisPrisonNumberOrNull(nomisPrisonNumber: String): VisitBalanceMappingDto? = webClient.get()
    .uri(
      "/mapping/visit-balance/nomis-prison-number/{nomisPrisonNumber}",
      nomisPrisonNumber,
    )
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun getByNomisPrisonNumber(nomisPrisonNumber: String): VisitBalanceMappingDto = webClient.get()
    .uri(
      "/mapping/visit-balance/nomis-prison-number/{nomisPrisonNumber}",
      nomisPrisonNumber,
    )
    .retrieve()
    .awaitBody()
}
