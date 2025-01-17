package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonMappingsDto

@Service
class CorePersonMappingApiService(@Qualifier("mappingApiWebClient") webClient: WebClient) : MigrationMapping<CorePersonMappingsDto>(domainUrl = "/mapping/core-person", webClient) {

  suspend fun createMappingsForMigration(mappings: CorePersonMappingsDto): CreateMappingResult<CorePersonMappingDto> = webClient.post()
    .uri("/mapping/core-person/migrate")
    .bodyValue(mappings)
    .retrieve()
    .bodyToMono(Unit::class.java)
    .map { CreateMappingResult<CorePersonMappingDto>() }
    .onErrorResume(WebClientResponseException.Conflict::class.java) {
      Mono.just(CreateMappingResult(it.getResponseBodyAs(object : ParameterizedTypeReference<DuplicateErrorResponse<CorePersonMappingDto>>() {})))
    }
    .awaitFirstOrDefault(CreateMappingResult())

  suspend fun getByNomisPrisonNumberOrNull(nomisPrisonNumber: String): CorePersonMappingDto? = webClient.get()
    .uri(
      "/mapping/core-person/person/nomis-prison-number/{nomisPrisonNumber}",
      nomisPrisonNumber,
    )
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun getByNomisPrisonNumber(nomisPrisonNumber: String): CorePersonMappingDto = webClient.get()
    .uri(
      "/mapping/core-person/person/nomis-prison-number/{nomisPrisonNumber}",
      nomisPrisonNumber,
    )
    .retrieve()
    .awaitBody()
}
