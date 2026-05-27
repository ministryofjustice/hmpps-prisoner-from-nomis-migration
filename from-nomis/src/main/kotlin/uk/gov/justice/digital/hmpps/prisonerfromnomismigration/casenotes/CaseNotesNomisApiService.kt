package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBodilessEntity
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CaseNoteResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerCaseNotesResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.UpdateCaseNoteRequest

@Service
class CaseNotesNomisApiService(@Qualifier("nomisApiWebClient") private val webClient: WebClient) {
  suspend fun getCaseNotesForPrisoner(offenderNo: String): PrisonerCaseNotesResponse = webClient.get()
    .uri("/prisoners/{offenderNo}/casenotes", offenderNo)
    .retrieve()
    .awaitBody()

  suspend fun getCaseNote(caseNoteId: Long): CaseNoteResponse = webClient.get()
    .uri("/casenotes/{caseNoteId}", caseNoteId)
    .retrieve()
    .awaitBody()

  suspend fun updateCaseNote(caseNoteId: Long, nomisCaseNote: UpdateCaseNoteRequest) {
    webClient.put()
      .uri("/casenotes/{caseNoteId}", caseNoteId)
      .bodyValue(nomisCaseNote)
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun deleteCaseNote(caseNoteId: Long) {
    webClient.delete()
      .uri("/casenotes/{caseNoteId}", caseNoteId)
      .retrieve()
      .awaitBodilessEntity()
  }
}
