package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes

import kotlinx.coroutines.reactive.awaitFirstOrDefault
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.CreateMappingResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.MigrationMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CaseNoteMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PrisonerCaseNoteMappingsDto

@Service
class CaseNotesByPrisonerMigrationMappingApiService(@Qualifier("mappingApiWebClient") webClient: WebClient) :
  MigrationMapping<CaseNoteMigrationMapping>(domainUrl = "/mapping/casenotes", webClient) {
  suspend fun createMapping(
    offenderNo: String,
    prisonerMapping: PrisonerCaseNoteMappingsDto,
    errorJavaClass: ParameterizedTypeReference<DuplicateErrorResponse<CaseNoteMappingDto>>,
  ): CreateMappingResult<CaseNoteMappingDto> {
    return webClient.post()
      .uri("/mapping/casenotes/{offenderNo}/all", offenderNo)
      .bodyValue(
        prisonerMapping,
      )
      .retrieve()
      .bodyToMono(Unit::class.java)
      .map { CreateMappingResult<CaseNoteMappingDto>() }
      .onErrorResume(WebClientResponseException.Conflict::class.java) {
        Mono.just(CreateMappingResult(it.getResponseBodyAs(errorJavaClass)))
      }
      .awaitFirstOrDefault(CreateMappingResult())
  }

  override suspend fun getMigrationCount(migrationId: String): Long = webClient.get()
    .uri("$domainUrl/migration-id/{migrationId}/count-by-prisoner", migrationId)
    .retrieve()
    .bodyToMono(Long::class.java)
    .onErrorResume(WebClientResponseException.NotFound::class.java) {
      Mono.empty()
    }
    .awaitSingleOrNull() ?: 0
}
