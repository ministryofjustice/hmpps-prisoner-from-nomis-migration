package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes

import kotlinx.coroutines.reactive.awaitFirstOrDefault
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.awaitBodilessEntity
import org.springframework.web.reactive.function.client.awaitBody
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.CreateMappingResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.MigrationMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CaseNoteMappingDto

@Service
class CaseNotesMappingApiService(@Qualifier("mappingApiWebClient") webClient: WebClient) :
  MigrationMapping<CaseNoteMappingDto>(domainUrl = "/mapping/casenotes", webClient) {
  suspend fun createMappings(
    mappings: List<CaseNoteMappingDto>,
    errorJavaClass: ParameterizedTypeReference<DuplicateErrorResponse<CaseNoteMappingDto>>,
  ): CreateMappingResult<CaseNoteMappingDto> =
    webClient.post()
      .uri("/mapping/casenotes/batch")
      .bodyValue(mappings)
      .retrieve()
      .bodyToMono(Unit::class.java)
      .map { CreateMappingResult<CaseNoteMappingDto>() }
      .onErrorResume(WebClientResponseException.Conflict::class.java) {
        Mono.just(CreateMappingResult(it.getResponseBodyAs(errorJavaClass)))
      }
      .awaitFirstOrDefault(CreateMappingResult())

  suspend fun deleteMappingGivenDpsId(dpsCaseNoteId: String) {
    webClient.delete()
      .uri("/mapping/casenotes/dps-casenote-id/{dpsCaseNoteId}", dpsCaseNoteId)
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun getMappingGivenNomisId(caseNoteId: Long): CaseNoteMappingDto =
    webClient.get()
      .uri("/mapping/casenotes/nomis-casenote-id/$caseNoteId")
      .retrieve()
      .awaitBody()
}
