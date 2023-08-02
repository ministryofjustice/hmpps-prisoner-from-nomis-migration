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
import java.time.LocalDateTime

@Service
class AdjudicationsMappingService(@Qualifier("mappingApiWebClient") webClient: WebClient) :
  MigrationMapping<AdjudicationMapping>(domainUrl = "/mapping/adjudications", webClient) {

  suspend fun findNomisMapping(adjudicationNumber: Long, chargeSequence: Int): AdjudicationMapping? {
    return webClient.get()
      .uri(
        "/mapping/adjudications/{adjudicationNumber}/charge-sequence/{chargeSequence}",
        adjudicationNumber,
        chargeSequence,
      )
      .retrieve()
      .bodyToMono(AdjudicationMapping::class.java)
      .onErrorResume(WebClientResponseException.NotFound::class.java) {
        Mono.empty()
      }
      .awaitSingleOrNull()
  }

  // overiding for now as adjudications will not have a problem with duplicates until mapping of lower level ids
  suspend fun createMapping(
    mapping: AdjudicationMapping,
  ): CreateMappingResult<AdjudicationMapping> {
    return webClient.post()
      .uri(domainUrl)
      .bodyValue(
        mapping,
      )
      .retrieve()
      .bodyToMono(Unit::class.java)
      .map { CreateMappingResult<AdjudicationMapping>() }
      .awaitFirstOrDefault(CreateMappingResult<AdjudicationMapping>())
  }
}

data class AdjudicationMapping(
  val adjudicationNumber: Long,
  val chargeSequence: Int,
  val mappingType: String,
  val label: String? = null,
  val whenCreated: LocalDateTime? = null,
)
