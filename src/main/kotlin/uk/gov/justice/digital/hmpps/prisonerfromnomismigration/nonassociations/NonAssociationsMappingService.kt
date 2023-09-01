package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nonassociations

import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.MigrationMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.NonAssociationMappingDto

@Service
class NonAssociationsMappingService(@Qualifier("mappingApiWebClient") webClient: WebClient) :
  MigrationMapping<NonAssociationMappingDto>(domainUrl = "/mapping/non-associations", webClient) {

  suspend fun findNomisNonAssociationMapping(
    firstOffenderNo: String,
    secondOffenderNo: String,
    nomisTypeSequence: Int,
  ): NonAssociationMappingDto? =
    webClient.get()
      .uri(
        "/mapping/non-associations/first-offender-no/{firstOffenderNo}/second-offender-no/{secondOffenderNo}",
        firstOffenderNo,
        secondOffenderNo,
        nomisTypeSequence,
      )
      .retrieve()
      .bodyToMono(NonAssociationMappingDto::class.java)
      .onErrorResume(WebClientResponseException.NotFound::class.java) {
        Mono.empty()
      }
      .awaitSingleOrNull()

/*
  TODO add delete mapping
  suspend fun deleteNomisNonAssociationMapping(
    nonAssociationId: Long,
  ): Unit =
    webClient.delete()
      .uri("/mapping/non-associations/non-association-id/$nonAssociationId")
      .retrieve()
      .awaitBody()
 */
}
