package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrNullWhenNotFound
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CaseNoteResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.PrisonerCaseNotesResponse

@Service
class CaseNotesNomisApiService(@Qualifier("nomisApiWebClient") private val webClient: WebClient) {
  suspend fun getCaseNotesToMigrate(offenderNo: String): PrisonerCaseNotesResponse? =
    webClient.get()
      .uri("/prisoners/{offenderNo}/casenotes", offenderNo)
      .retrieve()
      .awaitBodyOrNullWhenNotFound()

  suspend fun getCaseNote(caseNoteId: Long): CaseNoteResponse =
    webClient.get()
      .uri("/casenotes/{caseNoteId}", caseNoteId)
      .retrieve()
      .awaitBody()
}
