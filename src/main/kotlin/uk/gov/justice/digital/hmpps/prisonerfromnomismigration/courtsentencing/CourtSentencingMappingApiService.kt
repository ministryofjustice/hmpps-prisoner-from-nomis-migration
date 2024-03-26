package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBodilessEntity
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrNullWhenNotFound
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtCaseMappingDto

@Service
class CourtSentencingMappingApiService(@Qualifier("mappingApiWebClient") private val webClient: WebClient) {
  suspend fun getCourtCaseOrNullByNomisId(courtCase: Long): CourtCaseMappingDto? = webClient.get()
    .uri(
      "/mapping/court-sentencing/court-cases/nomis-court-case-id/{courtCase}",
      courtCase,
    )
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun createMapping(courtCaseMappingDto: CourtCaseMappingDto) {
    webClient.post()
      .uri("/mapping/court-sentencing/court-cases")
      .bodyValue(courtCaseMappingDto)
      .retrieve()
      .awaitBodilessEntity()
  }
}
