package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing

import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.domain.PageImpl
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.awaitBody
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CourtCaseIdResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CourtCaseResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CourtEventChargeResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CourtEventResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.OffenderChargeResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.SentenceResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.RestResponsePage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.typeReference
import java.time.LocalDate

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

  suspend fun getCourtCaseForMigration(courtCaseId: Long): CourtCaseResponse = webClient.get()
    .uri(
      "/court-cases/{courtCaseId}",
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

  suspend fun geLastModifiedCourtAppearanceCharge(offenderNo: String, offenderChargeId: Long): CourtEventChargeResponse = webClient.get()
    .uri(
      "/prisoners/{offenderNo}/sentencing/court-event-charges/{offenderChargeId}/last-modified",
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

  suspend fun getOffenderSentence(bookingId: Long, sentenceSequence: Int): SentenceResponse = webClient.get()
    .uri(
      "/prisoners/booking-id/{bookingId}/sentencing/sentence-sequence/{sentenceSequence}",
      bookingId,
      sentenceSequence,
    )
    .retrieve()
    .awaitBody()

  suspend fun getCourtCaseIds(
    fromDate: LocalDate?,
    toDate: LocalDate?,
    pageNumber: Long,
    pageSize: Long,
  ): PageImpl<CourtCaseIdResponse> =
    webClient.get()
      .uri {
        it.path("/court-cases/ids")
          .queryParam("fromDate", fromDate)
          .queryParam("toDate", toDate)
          .queryParam("page", pageNumber)
          .queryParam("size", pageSize)
          .build()
      }
      .retrieve()
      .bodyToMono(typeReference<RestResponsePage<CourtCaseIdResponse>>())
      .awaitSingle()
}
