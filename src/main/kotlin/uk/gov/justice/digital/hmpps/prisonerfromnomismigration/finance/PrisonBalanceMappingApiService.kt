package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance

import kotlinx.coroutines.reactive.awaitFirstOrDefault
import kotlinx.coroutines.reactor.awaitSingle
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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.api.PrisonBalanceMappingResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PrisonBalanceMappingDto

@Service
class PrisonBalanceMappingApiService(@Qualifier("mappingApiWebClient") webClient: WebClient) : MigrationMapping<PrisonBalanceMappingDto>(domainUrl = "/mapping/prison-balance", webClient) {
  private val api = PrisonBalanceMappingResourceApi(webClient)

  suspend fun createPrisonBalanceMapping(mappings: PrisonBalanceMappingDto): CreateMappingResult<PrisonBalanceMappingDto> = api
    .prepare(api.createPrisonMappingRequestConfig(mappings))
    .retrieve()
    .bodyToMono(Unit::class.java)
    .map { CreateMappingResult<PrisonBalanceMappingDto>() }
    .onErrorResume(WebClientResponseException.Conflict::class.java) {
      Mono.just(
        CreateMappingResult(
          it.getResponseBodyAs(object :
            ParameterizedTypeReference<DuplicateErrorResponse<PrisonBalanceMappingDto>>() {}),
        ),
      )
    }
    .awaitFirstOrDefault(CreateMappingResult())

  suspend fun getByNomisIdOrNull(nomisId: String): PrisonBalanceMappingDto? = api
    .prepare(api.getPrisonMappingByNomisIdRequestConfig(nomisId))
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun getByNomisId(nomisId: String): PrisonBalanceMappingDto = api
    .getPrisonMappingByNomisId(nomisId)
    .awaitSingle()
}
