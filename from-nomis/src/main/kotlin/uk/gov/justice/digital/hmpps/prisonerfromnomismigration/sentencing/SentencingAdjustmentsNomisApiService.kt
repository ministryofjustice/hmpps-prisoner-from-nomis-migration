package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrNullWhenNotFound
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.api.SentencingAdjustmentResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.KeyDateAdjustmentResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.SentenceAdjustmentResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.SentencingAdjustmentsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing.adjustments.model.LegacyAdjustment
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing.adjustments.model.LegacyAdjustment.AdjustmentType

@Service
class SentencingAdjustmentsNomisApiService(@Qualifier("nomisApiWebClient") webClient: WebClient) {
  private val sentencingAdjustmentResourceApi = SentencingAdjustmentResourceApi(webClient)

  suspend fun getAllByBookingId(bookingId: Long): SentencingAdjustmentsResponse = sentencingAdjustmentResourceApi
    .getActiveAdjustments(bookingId = bookingId, activeOnly = false).awaitSingle()

  suspend fun getSentenceAdjustment(
    nomisSentenceAdjustmentId: Long,
  ): SentenceAdjustmentResponse? = sentencingAdjustmentResourceApi.prepare(
    sentencingAdjustmentResourceApi.getSentenceAdjustmentRequestConfig(adjustmentId = nomisSentenceAdjustmentId),
  )
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun getKeyDateAdjustment(
    nomisKeyDateAdjustmentId: Long,
  ): KeyDateAdjustmentResponse? = sentencingAdjustmentResourceApi.prepare(
    sentencingAdjustmentResourceApi.getKeyDateAdjustmentRequestConfig(nomisKeyDateAdjustmentId),
  )
    .retrieve()
    .awaitBodyOrNullWhenNotFound()
}

fun SentenceAdjustmentResponse.toSentencingAdjustment() = LegacyAdjustment(
  bookingId = bookingId,
  sentenceSequence = sentenceSequence.toInt(),
  currentTerm = bookingSequence == 1,
  adjustmentType = AdjustmentType.valueOf(adjustmentType.code),
  adjustmentDate = adjustmentDate,
  adjustmentFromDate = adjustmentFromDate,
  adjustmentDays = adjustmentDays.toInt(),
  comment = comment,
  active = active,
  offenderNo = offenderNo,
  bookingReleased = hasBeenReleased,
  agencyId = prisonId,
)

fun KeyDateAdjustmentResponse.toSentencingAdjustment() = LegacyAdjustment(
  bookingId = bookingId,
  currentTerm = bookingSequence == 1,
  adjustmentType = AdjustmentType.valueOf(adjustmentType.code),
  adjustmentDate = adjustmentDate,
  adjustmentFromDate = adjustmentFromDate,
  adjustmentDays = adjustmentDays.toInt(),
  comment = comment,
  active = active,
  offenderNo = offenderNo,
  bookingReleased = hasBeenReleased,
  agencyId = prisonId,
)
