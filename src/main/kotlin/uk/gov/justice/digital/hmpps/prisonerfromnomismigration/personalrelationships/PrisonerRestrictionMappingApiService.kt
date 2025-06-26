package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrNullWhenNotFound
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.MigrationMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PrisonerRestrictionMappingDto

@Service
class PrisonerRestrictionMappingApiService(@Qualifier("mappingApiWebClient") webClient: WebClient) : MigrationMapping<PrisonerRestrictionMappingDto>(domainUrl = "/mapping/contact-person/prisoner-restriction", webClient) {
  suspend fun getByNomisPrisonerRestrictionIdOrNull(nomisPrisonerRestrictionId: Long): PrisonerRestrictionMappingDto? = webClient.get()
    .uri(
      "$domainUrl/nomis-prisoner-restriction-id/{nomisPrisonerRestrictionId}",
      nomisPrisonerRestrictionId,
    )
    .retrieve()
    .awaitBodyOrNullWhenNotFound()
}
