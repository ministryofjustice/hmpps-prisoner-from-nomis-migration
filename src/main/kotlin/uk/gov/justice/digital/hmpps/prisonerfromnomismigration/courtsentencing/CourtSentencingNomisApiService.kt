package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing

import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.awaitBody
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CourtCaseResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CourtEventResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.OffenderChargeResponse

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

  suspend fun getOffenderCharge(offenderNo: String, offenderChargeId: Long): OffenderChargeResponse = webClient.get()
    .uri(
      "/prisoners/{offenderNo}/sentencing/offender-charges/{offenderChargeId}",
      offenderNo,
      offenderChargeId,
    )
    .retrieve()
    .awaitBody()

  suspend fun getOffenderChargeOrNull(offenderNo: String, offenderChargeId: Long): OffenderChargeResponse? =
    webClient.get()
      .uri(
        "/prisoners/{offenderNo}/sentencing/offender-charges/{offenderChargeId}",
        offenderNo,
        offenderChargeId,
      )
      .retrieve()
      .bodyToMono(OffenderChargeResponse::class.java)
      .onErrorResume(WebClientResponseException.NotFound::class.java) {
        Mono.empty()
      }
      .awaitSingleOrNull()
}
