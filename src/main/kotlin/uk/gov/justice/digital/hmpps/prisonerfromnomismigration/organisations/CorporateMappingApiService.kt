package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations

import kotlinx.coroutines.reactive.awaitFirstOrDefault
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrNullWhenNotFound
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.CreateMappingResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.MigrationMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorporateMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorporateMappingsDto

@Service
class CorporateMappingApiService(@Qualifier("mappingApiWebClient") webClient: WebClient) : MigrationMapping<CorporateMappingsDto>(domainUrl = "/mapping/corporate/organisation", webClient) {
  suspend fun createMappingsForMigration(mappings: CorporateMappingsDto): CreateMappingResult<CorporateMappingDto> = webClient.post()
    .uri("/mapping/corporate/migrate")
    .bodyValue(mappings)
    .retrieve()
    .bodyToMono(Unit::class.java)
    .map { CreateMappingResult<CorporateMappingDto>() }
    .onErrorResume(WebClientResponseException.Conflict::class.java) {
      Mono.just(CreateMappingResult(it.getResponseBodyAs(object : ParameterizedTypeReference<DuplicateErrorResponse<CorporateMappingDto>>() {})))
    }
    .awaitFirstOrDefault(CreateMappingResult())

  suspend fun createCorporateMapping(mappings: CorporateMappingDto): CreateMappingResult<CorporateMappingDto> = webClient.post()
    .uri("/mapping/corporate/organisation")
    .bodyValue(mappings)
    .retrieve()
    .bodyToMono(Unit::class.java)
    .map { CreateMappingResult<CorporateMappingDto>() }
    .onErrorResume(WebClientResponseException.Conflict::class.java) {
      Mono.just(CreateMappingResult(it.getResponseBodyAs(object : ParameterizedTypeReference<DuplicateErrorResponse<CorporateMappingDto>>() {})))
    }
    .awaitFirstOrDefault(CreateMappingResult())

  suspend fun getByNomisCorporateIdOrNull(nomisCorporateId: Long): CorporateMappingDto? = webClient.get()
    .uri(
      "/mapping/corporate/organisation/nomis-corporate-id/{nomisCorporateId}",
      nomisCorporateId,
    )
    .retrieve()
    .awaitBodyOrNullWhenNotFound()
}
