package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBodilessEntity
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrNullWhenNotFound
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.MigrationMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CaseNoteMappingDto

@Service
class CaseNotesMappingApiService(@Qualifier("mappingApiWebClient") webClient: WebClient) :
  MigrationMapping<CaseNoteMappingDto>(domainUrl = "/mapping/casenotes", webClient) {
  suspend fun deleteMappingGivenDpsId(dpsCaseNoteId: String) {
    webClient.delete()
      .uri("/mapping/casenotes/dps-casenote-id/{dpsCaseNoteId}", dpsCaseNoteId)
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun getByDpsId(caseNoteId: String): List<CaseNoteMappingDto> = webClient.get()
    .uri(
      "/mapping/casenotes/dps-casenote-id/{casenoteId}/all",
      caseNoteId,
    )
    .retrieve()
    .awaitBody()

  suspend fun getMappingGivenNomisIdOrNull(caseNoteId: Long): CaseNoteMappingDto? =
    webClient.get()
      .uri("/mapping/casenotes/nomis-casenote-id/{caseNoteId}", caseNoteId)
      .retrieve()
      .awaitBodyOrNullWhenNotFound()

  suspend fun updateMappingsByNomisId(oldOffenderNo: String, newOffenderNo: String) {
    webClient.put()
      .uri("/mapping/casenotes/merge/from/{oldOffenderNo}/to/{newOffenderNo}", oldOffenderNo, newOffenderNo)
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun updateMappingsByBookingId(bookingId: Long, newOffenderNo: String): List<CaseNoteMappingDto> =
    webClient.put()
      .uri("/mapping/casenotes/merge/booking-id/{bookingId}/to/{newOffenderNo}", bookingId, newOffenderNo)
      .retrieve()
      .awaitBody()
}
