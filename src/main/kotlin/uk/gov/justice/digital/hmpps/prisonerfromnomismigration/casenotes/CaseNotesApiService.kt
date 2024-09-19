package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBodilessEntity
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes.model.MigrateCaseNoteRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes.model.MigrationResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes.model.SyncCaseNoteRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes.model.SyncResult

@Service
class CaseNotesApiService(@Qualifier("caseNotesApiWebClient") private val webClient: WebClient) {
  suspend fun upsertCaseNote(upsertRequest: SyncCaseNoteRequest): SyncResult =
    webClient.put()
      .uri("/sync/case-notes")
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(upsertRequest)
      .retrieve()
      .awaitBody()

  suspend fun deleteCaseNote(id: String) {
    webClient.delete()
      .uri("/sync/case-notes/{id}", id)
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun migrateCaseNotes(dpsCaseNotes: List<MigrateCaseNoteRequest>): List<MigrationResult> =
    webClient.post()
      .uri("/migrate/case-notes")
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(dpsCaseNotes)
      .retrieve()
      .awaitBody()
}
