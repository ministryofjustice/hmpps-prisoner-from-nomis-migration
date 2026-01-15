package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrNullWhenNotFound
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.SentencingAdjustmentsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing.adjustments.model.LegacyAdjustment
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing.adjustments.model.LegacyAdjustment.AdjustmentType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisCodeDescription
import java.time.LocalDate

@Service
class SentencingAdjustmentsNomisApiService(@Qualifier("nomisApiWebClient") private val webClient: WebClient) {
  suspend fun getAllByBookingId(bookingId: Long): SentencingAdjustmentsResponse = webClient.get()
    .uri(
      "/prisoners/booking-id/{bookingId}/sentencing-adjustments?active-only=false",
      bookingId,
    )
    .retrieve()
    .awaitBody()

  suspend fun getSentenceAdjustment(
    nomisSentenceAdjustmentId: Long,
  ): NomisAdjustment? = webClient.get()
    .uri("/sentence-adjustments/{nomisSentenceAdjustmentId}", nomisSentenceAdjustmentId)
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun getKeyDateAdjustment(
    nomisKeyDateAdjustmentId: Long,
  ): NomisAdjustment? = webClient.get()
    .uri("/key-date-adjustments/{nomisKeyDateAdjustmentId}", nomisKeyDateAdjustmentId)
    .retrieve()
    .awaitBodyOrNullWhenNotFound()
}

data class NomisAdjustment(
  val id: Long,
  val bookingId: Long,
  val bookingSequence: Int,
  val offenderNo: String,
  val sentenceSequence: Long? = null,
  val adjustmentType: NomisCodeDescription,
  val adjustmentDate: LocalDate?,
  val adjustmentFromDate: LocalDate?,
  val adjustmentToDate: LocalDate?,
  val adjustmentDays: Long,
  val comment: String?,
  val active: Boolean,
  val hiddenFromUsers: Boolean? = false,
  val hasBeenReleased: Boolean,
  val prisonId: String,
) {
  fun toSentencingAdjustment(): LegacyAdjustment = LegacyAdjustment(
    bookingId = bookingId,
    sentenceSequence = sentenceSequence?.toInt(),
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
}
