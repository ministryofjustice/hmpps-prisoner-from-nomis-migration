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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.VisitBalanceAdjustmentMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.VisitBalanceMappingDto

@Service
class VisitBalanceMappingApiService(@Qualifier("mappingApiWebClient") webClient: WebClient) : MigrationMapping<VisitBalanceMappingDto>(domainUrl = "/mapping/visit-balance", webClient) {

  suspend fun createVisitBalanceMapping(mappings: VisitBalanceMappingDto): CreateMappingResult<VisitBalanceMappingDto> = webClient.post()
    .uri("/mapping/visit-balance")
    .bodyValue(mappings)
    .retrieve()
    .bodyToMono(Unit::class.java)
    .map { CreateMappingResult<VisitBalanceMappingDto>() }
    .onErrorResume(WebClientResponseException.Conflict::class.java) {
      Mono.just(CreateMappingResult(it.getResponseBodyAs(object : ParameterizedTypeReference<DuplicateErrorResponse<VisitBalanceMappingDto>>() {})))
    }
    .awaitFirstOrDefault(CreateMappingResult())

  suspend fun getByNomisVisitBalanceIdOrNull(nomisVisitBalanceId: Long): VisitBalanceMappingDto? = webClient.get()
    .uri(
      "/mapping/visit-balance/nomis-id/{nomisVisitBalanceId}",
      nomisVisitBalanceId,
    )
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun getByNomisVisitBalanceId(nomisVisitBalanceId: Long): VisitBalanceMappingDto = webClient.get()
    .uri(
      "/mapping/visit-balance/nomis-id/{nomisVisitBalanceId}",
      nomisVisitBalanceId,
    )
    .retrieve()
    .awaitBody()

  suspend fun createVisitBalanceAdjustmentMapping(mapping: VisitBalanceAdjustmentMappingDto): CreateMappingResult<VisitBalanceAdjustmentMappingDto> = webClient.post()
    .uri("/mapping/visit-balance-adjustment")
    .bodyValue(mapping)
    .retrieve()
    .bodyToMono(Unit::class.java)
    .map { CreateMappingResult<VisitBalanceAdjustmentMappingDto>() }
    .onErrorResume(WebClientResponseException.Conflict::class.java) {
      Mono.just(CreateMappingResult(it.getResponseBodyAs(object : ParameterizedTypeReference<DuplicateErrorResponse<VisitBalanceAdjustmentMappingDto>>() {})))
    }
    .awaitFirstOrDefault(CreateMappingResult())

  suspend fun getByNomisVisitBalanceAdjustmentIdOrNull(nomisVisitBalanceAdjustmentId: Long): VisitBalanceAdjustmentMappingDto? = webClient.get()
    .uri(
      "/mapping/visit-balance-adjustment/nomis-id/{nomisVisitBalanceAdjustmentId}",
      nomisVisitBalanceAdjustmentId,
    )
    .retrieve()
    .awaitBodyOrNullWhenNotFound()
}
