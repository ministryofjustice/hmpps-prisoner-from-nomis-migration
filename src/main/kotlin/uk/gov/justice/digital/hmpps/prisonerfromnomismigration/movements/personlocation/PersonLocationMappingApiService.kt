package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.personlocation

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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.api.PersonLocationMigrationResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonLocationsMappingDto

@Service
class PersonLocationMappingApiService(@Qualifier("personLocationMappingApiWebClient") webClient: WebClient) : MigrationMapping<PersonLocationsMappingDto>(domainUrl = "/mapping/person-location", webClient) {

  private val migrationApi = PersonLocationMigrationResourceApi(webClient)

  override suspend fun createMapping(
    mapping: PersonLocationsMappingDto,
    errorJavaClass: ParameterizedTypeReference<DuplicateErrorResponse<PersonLocationsMappingDto>>,
  ): CreateMappingResult<PersonLocationsMappingDto> = migrationApi.prepare(migrationApi.createPersonLocationMappingsRequestConfig(mapping))
    .retrieve()
    .bodyToMono<Unit>()
    .map { CreateMappingResult<PersonLocationsMappingDto>() }
    .onErrorResume(WebClientResponseException.Conflict::class.java) {
      Mono.just(CreateMappingResult(it.getResponseBodyAs(errorJavaClass)))
    }
    .awaitFirstOrDefault(CreateMappingResult())
}
