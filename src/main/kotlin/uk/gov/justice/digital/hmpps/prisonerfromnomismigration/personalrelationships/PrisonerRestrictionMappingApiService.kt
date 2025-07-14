package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBodilessEntity
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrNullWhenNotFound
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.MigrationMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PrisonerRestrictionMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PrisonerRestrictionMappingsDto

@Service
class PrisonerRestrictionMappingApiService(@Qualifier("mappingApiWebClient") webClient: WebClient) : MigrationMapping<PrisonerRestrictionMappingDto>(domainUrl = "/mapping/contact-person/prisoner-restriction", webClient) {
  suspend fun getByNomisPrisonerRestrictionIdOrNull(nomisPrisonerRestrictionId: Long): PrisonerRestrictionMappingDto? = webClient.get()
    .uri(
      "$domainUrl/nomis-prisoner-restriction-id/{nomisPrisonerRestrictionId}",
      nomisPrisonerRestrictionId,
    )
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun getByNomisPrisonerRestrictionId(nomisPrisonerRestrictionId: Long): PrisonerRestrictionMappingDto = webClient.get()
    .uri(
      "$domainUrl/nomis-prisoner-restriction-id/{nomisPrisonerRestrictionId}",
      nomisPrisonerRestrictionId,
    )
    .retrieve()
    .awaitBody()

  suspend fun deleteByNomisPrisonerRestrictionId(nomisPrisonerRestrictionId: Long) {
    webClient.delete()
      .uri(
        "$domainUrl/nomis-prisoner-restriction-id/{nomisPrisonerRestrictionId}",
        nomisPrisonerRestrictionId,
      )
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun replace(offenderNo: String, mappings: PrisonerRestrictionMappingsDto) {
    webClient.post()
      .uri("/mapping/contact-person/replace/prisoner-restrictions/{offenderNo}", offenderNo)
      .bodyValue(mappings)
      .retrieve()
      .awaitBodilessEntity()
  }
  suspend fun replaceAfterMerge(retainedOffenderNo: String, removedOffenderNo: String, mappings: PrisonerRestrictionMappingsDto) {
    webClient.post()
      .uri("/mapping/contact-person/replace/prisoner-restrictions/{retainedOffenderNo}/replaces/{removedOffenderNo}", retainedOffenderNo, removedOffenderNo)
      .bodyValue(mappings)
      .retrieve()
      .awaitBodilessEntity()
  }
}
