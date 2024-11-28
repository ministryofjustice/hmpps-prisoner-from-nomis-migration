package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBodilessEntity
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrNullWhenNotFound
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CaseNoteResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.PrisonerCaseNotesResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.UpdateCaseNoteRequest

@Service
class CaseNotesNomisApiService(@Qualifier("nomisApiWebClient") private val webClient: WebClient) {
  suspend fun getCaseNotesForPrisonerOrNull(offenderNo: String): PrisonerCaseNotesResponse? =
    webClient.get()
      .uri("/prisoners/{offenderNo}/casenotes", offenderNo)
      .retrieve()
      .awaitBodyOrNullWhenNotFound()

  suspend fun getCaseNotesForPrisoner(offenderNo: String): PrisonerCaseNotesResponse =
    webClient.get()
      .uri("/prisoners/{offenderNo}/casenotes", offenderNo)
      .retrieve()
      .awaitBody()

  suspend fun getCaseNote(caseNoteId: Long): CaseNoteResponse =
    webClient.get()
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
}
