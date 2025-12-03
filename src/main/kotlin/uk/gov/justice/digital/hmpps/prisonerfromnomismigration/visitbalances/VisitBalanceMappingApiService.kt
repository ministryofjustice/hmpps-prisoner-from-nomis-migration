package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visitbalances

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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.api.VisitBalanceAdjustmentMappingResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.VisitBalanceAdjustmentMappingDto

@Service
class VisitBalanceMappingApiService(@Qualifier("mappingApiWebClient") webClient: WebClient) : MigrationMapping<VisitBalanceAdjustmentMappingDto>(domainUrl = "/mapping/visit-balance", webClient) {
  private val adjustmentApi = VisitBalanceAdjustmentMappingResourceApi(webClient)
  suspend fun createVisitBalanceAdjustmentMapping(mapping: VisitBalanceAdjustmentMappingDto): CreateMappingResult<VisitBalanceAdjustmentMappingDto> = adjustmentApi
    .prepare(adjustmentApi.createMapping2RequestConfig(mapping))
    .retrieve()
    .bodyToMono(Unit::class.java)
    .map { CreateMappingResult<VisitBalanceAdjustmentMappingDto>() }
    .onErrorResume(WebClientResponseException.Conflict::class.java) {
      Mono.just(CreateMappingResult(it.getResponseBodyAs(object : ParameterizedTypeReference<DuplicateErrorResponse<VisitBalanceAdjustmentMappingDto>>() {})))
    }
    .awaitFirstOrDefault(CreateMappingResult())

  suspend fun getByNomisVisitBalanceAdjustmentIdOrNull(nomisVisitBalanceAdjustmentId: Long): VisitBalanceAdjustmentMappingDto? = adjustmentApi
    .prepare(adjustmentApi.getMappingByNomisId1RequestConfig(nomisVisitBalanceAdjustmentId))
    .retrieve()
    .awaitBodyOrNullWhenNotFound()
}
