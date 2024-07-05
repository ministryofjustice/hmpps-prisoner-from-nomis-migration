package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBodilessEntity
import org.springframework.web.reactive.function.client.awaitBody

@Service
class CaseNotesApiService(@Qualifier("caseNotesApiWebClient") private val webClient: WebClient) {
  suspend fun upsertCaseNote(upsertRequest: SyncCaseNoteRequest): DpsCaseNote =
    webClient.post()
      .uri("/sync/upsert")
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(upsertRequest)
      .retrieve()
      .awaitBody()

  suspend fun deleteCaseNote(id: String) {
    webClient.delete()
      .uri("/sync/delete/{id}", id)
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun migrateCaseNotes(offenderNo: String, dpsCaseNotes: List<MigrateCaseNoteRequest>): List<DpsCaseNote> =
    webClient.post()
      .uri("/migrate/{offenderNo}/casenotes", offenderNo)
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(dpsCaseNotes)
      .retrieve()
      .awaitBody()
}

data class MigrateCaseNoteRequest(
  val dummyAttribute: String? = null,
)

data class SyncCaseNoteRequest(
  val caseNoteId: String? = null,
  val dummyAttribute: String? = null,
)

data class DpsCaseNote(
  val caseNoteId: String? = null,
  val dummyAttribute: String? = null,
)

data class CaseNotesForPrisonerResponse(
  val caseNotes: List<DpsCaseNote>,
)
