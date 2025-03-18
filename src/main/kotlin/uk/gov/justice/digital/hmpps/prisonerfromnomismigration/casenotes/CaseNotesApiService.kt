package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBodilessEntity
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes.model.MoveCaseNotesRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes.model.SyncCaseNoteRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes.model.SyncResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodilessEntityOrLogAndRethrowBadRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrLogAndRethrowBadRequest

@Service
class CaseNotesApiService(@Qualifier("caseNotesApiWebClient") private val webClient: WebClient) {
  suspend fun upsertCaseNote(upsertRequest: SyncCaseNoteRequest): SyncResult = webClient.put()
    .uri("/sync/case-notes")
    .contentType(MediaType.APPLICATION_JSON)
    .bodyValue(upsertRequest)
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun deleteCaseNote(id: String) {
    webClient.delete()
      .uri("/sync/case-notes/{id}", id)
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun moveCaseNotes(request: MoveCaseNotesRequest) {
    webClient.put()
      .uri("/move/case-notes")
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(request)
      .retrieve()
      .awaitBodilessEntityOrLogAndRethrowBadRequest()
  }
}
