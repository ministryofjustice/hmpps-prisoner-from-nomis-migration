package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing

import kotlinx.coroutines.reactive.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrNullWhenNotFound
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.api.CourtSentencingResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CourtCaseResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CourtEventChargeIdsWithLast
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CourtEventChargeResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CourtEventResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderChargeResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PostPrisonerMergeCaseChanges
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.SentenceResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.SentenceTermResponse

@Service
class CourtSentencingNomisApiService(@Qualifier("nomisApiWebClient") private val webClient: WebClient) {
  private val api = CourtSentencingResourceApi(webClient)

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

  suspend fun getCourtCasesForMigration(offenderNo: String): List<CourtCaseResponse> = webClient.get()
    .uri(
      "/prisoners/{offenderNo}/sentencing/court-cases",
      offenderNo,
    )
    .retrieve()
    .awaitBody()

  suspend fun getCourtCases(offenderNo: String, courtCaseIds: List<Long>): List<CourtCaseResponse> = webClient.post()
    .uri(
      "/prisoners/{offenderNo}/sentencing/court-cases/get-list",
      offenderNo,
    )
    .bodyValue(courtCaseIds)
    .retrieve()
    .awaitBody()

  suspend fun getCourtCasesChangedByMerge(offenderNo: String): PostPrisonerMergeCaseChanges = webClient.get()
    .uri(
      "/prisoners/{offenderNo}/sentencing/court-cases/post-merge",
      offenderNo,
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

  suspend fun getCourtAppearanceNullable(offenderNo: String, courtAppearanceId: Long): CourtEventResponse? = webClient.get()
    .uri(
      "/prisoners/{offenderNo}/sentencing/court-appearances/{courtAppearanceId}",
      offenderNo,
      courtAppearanceId,
    )
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun getOffenderCharge(offenderNo: String, offenderChargeId: Long): OffenderChargeResponse = webClient.get()
    .uri(
      "/prisoners/{offenderNo}/sentencing/offender-charges/{offenderChargeId}",
      offenderNo,
      offenderChargeId,
    )
    .retrieve()
    .awaitBody()

  suspend fun getCourtEventCharge(eventId: Long, offenderNo: String, offenderChargeId: Long): CourtEventChargeResponse = webClient.get()
    .uri(
      "/prisoners/{offenderNo}/sentencing/court-appearances/{eventId}/charges/{offenderChargeId}",
      offenderNo,
      eventId,
      offenderChargeId,
    )
    .retrieve()
    .awaitBody()

  suspend fun getCourtEventChargeOrNull(
    eventId: Long,
    offenderNo: String,
    offenderChargeId: Long,
  ): CourtEventChargeResponse? = webClient.get()
    .uri(
      "/prisoners/{offenderNo}/sentencing/court-appearances/{eventId}/charges/{offenderChargeId}",
      offenderNo,
      eventId,
      offenderChargeId,
    )
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun getOffenderSentence(offenderNo: String, caseId: Long, sentenceSequence: Int): SentenceResponse = webClient.get()
    .uri(
      "/prisoners/{offenderNo}/court-cases/{caseId}/sentences/{sentenceSequence}",
      offenderNo,
      caseId,
      sentenceSequence,
    )
    .retrieve()
    .awaitBody()

  suspend fun getOffenderSentenceNullable(offenderNo: String, caseId: Long, sentenceSequence: Int): SentenceResponse? = webClient.get()
    .uri(
      "/prisoners/{offenderNo}/court-cases/{caseId}/sentences/{sentenceSequence}",
      offenderNo,
      caseId,
      sentenceSequence,
    )
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun getOffenderSentenceByBookingNullable(bookingId: Long, sentenceSequence: Int): SentenceResponse? = webClient.get()
    .uri(
      "/prisoners/booking-id/{bookingId}/sentences/{sentenceSequence}",
      bookingId,
      sentenceSequence,
    )
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  // version without caseId for sentence charge events
  suspend fun getOffenderSentenceByBooking(bookingId: Long, sentenceSequence: Int): SentenceResponse = webClient.get()
    .uri(
      "/prisoners/booking-id/{bookingId}/sentences/{sentenceSequence}",
      bookingId,
      sentenceSequence,
    )
    .retrieve()
    .awaitBody()

  suspend fun getOffenderSentenceTerm(offenderNo: String, bookingId: Long, sentenceSequence: Int, termSequence: Int): SentenceTermResponse = webClient.get()
    .uri(
      "/prisoners/{offenderNo}/sentence-terms/booking-id/{bookingId}/sentence-sequence/{sentenceSequence}/term-sequence/{termSequence}",
      offenderNo,
      bookingId,
      sentenceSequence,
      termSequence,
    )
    .retrieve()
    .awaitBody()

  suspend fun getOffenderSentenceTermNullable(offenderNo: String, bookingId: Long, sentenceSequence: Int, termSequence: Int): SentenceTermResponse? = webClient.get()
    .uri(
      "/prisoners/{offenderNo}/sentence-terms/booking-id/{bookingId}/sentence-sequence/{sentenceSequence}/term-sequence/{termSequence}",
      offenderNo,
      bookingId,
      sentenceSequence,
      termSequence,
    )
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun getOffenderActiveRecallSentences(bookingId: Long): List<SentenceResponse> = webClient.get()
    .uri(
      "/prisoners/booking-id/{bookingId}/sentences/recall",
      bookingId,
    )
    .retrieve()
    .awaitBody()

  suspend fun getCourtEventChargeIdsWithPropertyValueFromId(
    lastOffenderChargeId: Long = 0,
    lastEventId: Long = 0,
    pageSize: Int = 20,
  ): CourtEventChargeIdsWithLast = api
    .getCourtEventChargeIdsWithPropertyValueFromId(
      offenderChargeId = lastOffenderChargeId,
      eventId = lastEventId,
      pageSize = pageSize,
    )
    .awaitSingle()

  suspend fun getCourtEventChargeForMigration(eventId: Long, offenderChargeId: Long): CourtEventChargeResponse = api
    .getCourtEventChargeForMigration(chargeId = offenderChargeId, eventId = eventId)
    .awaitSingle()
}
