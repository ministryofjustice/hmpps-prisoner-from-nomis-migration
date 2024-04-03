package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBodilessEntity
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrNullWhenNotFound
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.MigrationMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtCaseAllMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtCaseMappingDto

@Service
class CourtSentencingMappingApiService(@Qualifier("mappingApiWebClient") webClient: WebClient) :
  MigrationMapping<CourtCaseAllMappingDto>(domainUrl = "/mapping/court-sentencing/court-cases", webClient) {
  suspend fun getCourtCaseOrNullByNomisId(courtCaseId: Long): CourtCaseMappingDto? = webClient.get()
    .uri(
      "/mapping/court-sentencing/court-cases/nomis-court-case-id/{courtCase}",
      courtCaseId,
    )
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun deleteCourtCaseMappingByDpsId(courtCaseId: String) = webClient.delete()
    .uri(
      "/mapping/court-sentencing/court-cases/dps-court-case-id/{courtCase}",
      courtCaseId,
    )
    .retrieve()
    .awaitBodilessEntity()
}
