package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CourtCaseResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CourtEventResponse

@Service
class CourtSentencingNomisApiService(@Qualifier("nomisApiWebClient") private val webClient: WebClient) {
  suspend fun getCourtCase(offenderNo: String, courtCaseId: Long): CourtCaseResponse = webClient.get()
    .uri(
      "/prisoners/{offenderNo}/sentencing/court-cases/{courtCaseId}",
      offenderNo,
      courtCaseId,
    )
    .retrieve()
    .awaitBody()

  suspend fun getCourtAppearance(offenderNo: String, courtAppearanceId: Long): CourtEventResponse = webClient.get()
    .uri(
      "/prisoners/{offenderNo}/sentencing/court-appearances/{courtAppearanceId}",
      offenderNo,
      courtAppearanceId,
    )
    .retrieve()
    .awaitBody()
}
