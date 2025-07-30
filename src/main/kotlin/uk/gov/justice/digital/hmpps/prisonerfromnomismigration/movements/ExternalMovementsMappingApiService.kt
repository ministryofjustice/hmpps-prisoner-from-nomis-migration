package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements

import com.fasterxml.jackson.annotation.JsonProperty
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

@Service
class ExternalMovementsMappingApiService(@Qualifier("mappingApiWebClient") webClient: WebClient) : MigrationMapping<ExternalMovementsMigrationMappingDto>(domainUrl = "/mapping/external-movements/migration", webClient) {
  override suspend fun createMapping(
    mapping: ExternalMovementsMigrationMappingDto,
    errorJavaClass: ParameterizedTypeReference<DuplicateErrorResponse<ExternalMovementsMigrationMappingDto>>,
  ): CreateMappingResult<ExternalMovementsMigrationMappingDto> = webClient.put()
    .uri(createMappingUrl())
    .bodyValue(
      mapping,
    )
    .retrieve()
    .bodyToMono(Unit::class.java)
    .map { CreateMappingResult<ExternalMovementsMigrationMappingDto>() }
    .onErrorResume(WebClientResponseException.Conflict::class.java) {
      Mono.just(CreateMappingResult(it.getResponseBodyAs(errorJavaClass)))
    }
    .awaitFirstOrDefault(CreateMappingResult())
}

// TODO SDIT-2873 This is a placeholder - replace with generated DTO when available
data class ExternalMovementsMigrationMappingDto(

  /* NOMIS prisoner number */
  @get:JsonProperty("prisonerNumber")
  val prisonerNumber: String,

  /* Migration Id */
  @get:JsonProperty("migrationId")
  val migrationId: String,

  /* Date time the mapping was created */
  @get:JsonProperty("whenCreated")
  val whenCreated: String? = null,
)
