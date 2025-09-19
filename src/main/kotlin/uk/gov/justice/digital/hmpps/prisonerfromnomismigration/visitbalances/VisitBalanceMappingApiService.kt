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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.api.VisitBalanceAdjustmentMappingResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.api.VisitBalanceMappingResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.VisitBalanceAdjustmentMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.VisitBalanceMappingDto

@Service
class VisitBalanceMappingApiService(@Qualifier("mappingApiWebClient") webClient: WebClient) : MigrationMapping<VisitBalanceMappingDto>(domainUrl = "/mapping/visit-balance", webClient) {
  private val api = VisitBalanceMappingResourceApi(webClient)
  private val adjustmentApi = VisitBalanceAdjustmentMappingResourceApi(webClient)

  suspend fun createVisitBalanceMapping(mappings: VisitBalanceMappingDto): CreateMappingResult<VisitBalanceMappingDto> = api
    .prepare(api.createMapping3RequestConfig(mappings))
    .retrieve()
    .bodyToMono(Unit::class.java)
    .map { CreateMappingResult<VisitBalanceMappingDto>() }
    .onErrorResume(WebClientResponseException.Conflict::class.java) {
      Mono.just(CreateMappingResult(it.getResponseBodyAs(object : ParameterizedTypeReference<DuplicateErrorResponse<VisitBalanceMappingDto>>() {})))
    }
    .awaitFirstOrDefault(CreateMappingResult())

  suspend fun getByNomisVisitBalanceIdOrNull(nomisVisitBalanceId: Long): VisitBalanceMappingDto? = api
    .prepare(api.getMappingByNomisId1RequestConfig(nomisVisitBalanceId))
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun getByNomisVisitBalanceId(nomisVisitBalanceId: Long): VisitBalanceMappingDto = api
    .prepare(api.getMappingByNomisId1RequestConfig(nomisVisitBalanceId))
    .retrieve()
    .awaitBody()

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
    .prepare(adjustmentApi.getMappingByNomisId2RequestConfig(nomisVisitBalanceAdjustmentId))
    .retrieve()
    .awaitBodyOrNullWhenNotFound()
}
