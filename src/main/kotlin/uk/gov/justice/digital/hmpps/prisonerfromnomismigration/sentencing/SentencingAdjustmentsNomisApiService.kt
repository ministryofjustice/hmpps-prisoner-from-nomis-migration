package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.domain.PageImpl
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrNullWhenNotFound
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.AdjustmentIdResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.SentencingAdjustmentsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing.adjustments.model.LegacyAdjustment
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing.adjustments.model.LegacyAdjustment.AdjustmentType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisCodeDescription
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.RestResponsePage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.typeReference
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

  suspend fun getSentencingAdjustmentIds(
    fromDate: LocalDate? = null,
    toDate: LocalDate? = null,
    pageNumber: Long = 0,
    pageSize: Long = 20,
  ): PageImpl<AdjustmentIdResponse> = webClient.get()
    .uri {
      it.path("/adjustments/ids")
        .queryParam("fromDate", fromDate)
        .queryParam("toDate", toDate)
        .queryParam("page", pageNumber)
        .queryParam("size", pageSize)
        .build()
    }
    .retrieve()
    .bodyToMono(typeReference<RestResponsePage<AdjustmentIdResponse>>())
    .awaitSingle()
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
  val hiddenFromUsers: Boolean,
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
