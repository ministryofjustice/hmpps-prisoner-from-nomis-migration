package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts

import kotlinx.coroutines.reactive.awaitFirstOrDefault
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationDetails
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.CreateMappingResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.MigrationMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.AlertMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PrisonerAlertMappingsDto

@Service
class AlertsByPrisonerMigrationMappingApiService(@Qualifier("mappingApiWebClient") webClient: WebClient) :
  MigrationMapping<AlertMigrationMapping>(domainUrl = "/mapping/alerts/all", webClient) {
  suspend fun createMapping(
    offenderNo: String,
    prisonerMapping: PrisonerAlertMappingsDto,
    errorJavaClass: ParameterizedTypeReference<DuplicateErrorResponse<AlertMappingDto>>,
  ): CreateMappingResult<AlertMappingDto> {
    return webClient.post()
      .uri("/mapping/alerts/{offenderNo}/all", offenderNo)
      .bodyValue(
        prisonerMapping,
      )
      .retrieve()
      .bodyToMono(Unit::class.java)
      .map { CreateMappingResult<AlertMappingDto>() }
      .onErrorResume(WebClientResponseException.Conflict::class.java) {
        Mono.just(CreateMappingResult(it.getResponseBodyAs(errorJavaClass)))
      }
      .awaitFirstOrDefault(CreateMappingResult())
  }

  override suspend fun getMigrationCount(migrationId: String): Long = webClient.get()
    .uri {
      it.path("/mapping/alerts/migration-id/{migrationId}/grouped-by-prisoner")
        .queryParam("size", 1)
        .build(migrationId)
    }
    .retrieve()
    .bodyToMono(MigrationDetails::class.java)
    .onErrorResume(WebClientResponseException.NotFound::class.java) {
      Mono.empty()
    }
    .awaitSingleOrNull()?.count ?: 0
}
