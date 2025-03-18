package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes

import kotlinx.coroutines.reactive.awaitFirstOrDefault
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.CreateMappingResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CaseNoteMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PrisonerCaseNoteMappingsDto

@Service
class CaseNotesByPrisonerMigrationMappingApiService(@Qualifier("mappingApiWebClient") private val webClient: WebClient) {
  suspend fun createMapping(
    offenderNo: String,
    prisonerMapping: PrisonerCaseNoteMappingsDto,
    errorJavaClass: ParameterizedTypeReference<DuplicateErrorResponse<CaseNoteMappingDto>>,
  ): CreateMappingResult<CaseNoteMappingDto> = webClient.post()
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

  suspend fun createMappings(
    mappings: List<CaseNoteMappingDto>,
    errorJavaClass: ParameterizedTypeReference<DuplicateErrorResponse<CaseNoteMappingDto>>,
  ): CreateMappingResult<CaseNoteMappingDto> = webClient.post()
    .uri("/mapping/casenotes/batch")
    .bodyValue(mappings)
    .retrieve()
    .bodyToMono(Unit::class.java)
    .map { CreateMappingResult<CaseNoteMappingDto>() }
    .onErrorResume(WebClientResponseException.Conflict::class.java) {
      Mono.just(CreateMappingResult(it.getResponseBodyAs(errorJavaClass)))
    }
    .awaitFirstOrDefault(CreateMappingResult())
}
