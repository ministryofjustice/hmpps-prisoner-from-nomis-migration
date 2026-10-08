package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.prisonerlocation

import kotlinx.coroutines.reactive.awaitFirstOrDefault
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.CreateMappingResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.MigrationMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.api.PrisonerLocationMigrationResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PrisonerLocationsMappingDto

@Service
class PrisonerLocationMappingApiService(@Qualifier("prisonerLocationMappingApiWebClient") webClient: WebClient) : MigrationMapping<PrisonerLocationsMappingDto>(domainUrl = "/mapping/prisoner-location", webClient) {

  private val migrationApi = PrisonerLocationMigrationResourceApi(webClient)

  override suspend fun createMapping(
    mapping: PrisonerLocationsMappingDto,
    errorJavaClass: ParameterizedTypeReference<DuplicateErrorResponse<PrisonerLocationsMappingDto>>,
  ): CreateMappingResult<PrisonerLocationsMappingDto> = migrationApi.prepare(migrationApi.createPrisonerLocationMappingsRequestConfig(mapping))
    .retrieve()
    .bodyToMono<Unit>()
    .map { CreateMappingResult<PrisonerLocationsMappingDto>() }
    .onErrorResume(WebClientResponseException.Conflict::class.java) {
      Mono.just(CreateMappingResult(it.getResponseBodyAs(errorJavaClass)))
    }
    .awaitFirstOrDefault(CreateMappingResult())
}
