package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications

import kotlinx.coroutines.reactive.awaitFirstOrDefault
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.CreateMappingResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.MigrationMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.AdjudicationAllMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.AdjudicationMappingDto

@Service
class AdjudicationsMappingService(@Qualifier("mappingApiWebClient") webClient: WebClient) :
  MigrationMapping<AdjudicationAllMappingDto>(domainUrl = "/mapping/adjudications", webClient) {

  override fun createMappingUrl(): String {
    return super.createMappingUrl() + "/all"
  }

  suspend fun findNomisMapping(adjudicationNumber: Long, chargeSequence: Int): AdjudicationMappingDto? {
    return webClient.get()
      .uri(
        "/mapping/adjudications/adjudication-number/{adjudicationNumber}/charge-sequence/{chargeSequence}",
        adjudicationNumber,
        chargeSequence,
      )
      .retrieve()
      .bodyToMono(AdjudicationMappingDto::class.java)
      .onErrorResume(WebClientResponseException.NotFound::class.java) {
        Mono.empty()
      }
      .awaitSingleOrNull()
  }

  suspend fun createMapping(
    mapping: AdjudicationAllMappingDto,
  ): CreateMappingResult<AdjudicationAllMappingDto> {
    return webClient.post()
      .uri(createMappingUrl())
      .bodyValue(
        mapping,
      )
      .retrieve()
      .bodyToMono(Unit::class.java)
      .map { CreateMappingResult<AdjudicationAllMappingDto>() }
      .awaitFirstOrDefault(CreateMappingResult())
  }
}
