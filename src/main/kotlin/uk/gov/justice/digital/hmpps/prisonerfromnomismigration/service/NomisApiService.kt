package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.JsonNode
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.ParameterizedTypeReference
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incentives.CreateIncentiveIEP
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incentives.ReviewType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incentives.UpdateIncentiveIEP
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing.CreateSentenceAdjustment
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visits.VisitRoomUsageResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visits.VisitsMigrationFilter
import java.time.LocalDate
import java.time.LocalDateTime

@Service
class NomisApiService(@Qualifier("nomisApiWebClient") private val webClient: WebClient) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
  suspend fun getVisits(
    prisonIds: List<String>,
    visitTypes: List<String>,
    fromDateTime: LocalDateTime?,
    toDateTime: LocalDateTime?,
    ignoreMissingRoom: Boolean,
    pageNumber: Long,
    pageSize: Long,
  ): PageImpl<VisitId> =
    webClient.get()
      .uri {
        it.path("/visits/ids")
          .queryParam("prisonIds", prisonIds)
          .queryParam("visitTypes", visitTypes)
          .queryParam("fromDateTime", fromDateTime)
          .queryParam("toDateTime", toDateTime)
          .queryParam("ignoreMissingRoom", ignoreMissingRoom)
          .queryParam("page", pageNumber)
          .queryParam("size", pageSize)
          .build()
      }
      .retrieve()
      .bodyToMono(typeReference<RestResponsePage<VisitId>>())
      .awaitSingle()

  suspend fun getVisit(
    nomisVisitId: Long,
  ): NomisVisit =
    webClient.get()
      .uri("/visits/{nomisVisitId}", nomisVisitId)
      .retrieve()
      .bodyToMono(NomisVisit::class.java)
      .awaitSingle()!!

  suspend fun getRoomUsage(
    filter: VisitsMigrationFilter
  ): List<VisitRoomUsageResponse> =
    webClient.get()
      .uri {
        it.path("/visits/rooms/usage-count")
          .queryParam("prisonIds", filter.prisonIds)
          .queryParam("visitTypes", filter.visitTypes)
          .queryParam("fromDateTime", filter.fromDateTime)
          .queryParam("toDateTime", filter.toDateTime)
          .build()
      }
      .retrieve()
      .bodyToMono(typeReference<List<VisitRoomUsageResponse>>())
      .awaitSingle()

  suspend fun getIncentives(
    fromDate: LocalDate?,
    toDate: LocalDate?,
    pageNumber: Long,
    pageSize: Long
  ): PageImpl<IncentiveId> =
    webClient.get()
      .uri {
        it.path("/incentives/ids")
          .queryParam("fromDate", fromDate)
          .queryParam("toDate", toDate)
          .queryParam("page", pageNumber)
          .queryParam("size", pageSize)
          .build()
      }
      .retrieve()
      .bodyToMono(typeReference<RestResponsePage<IncentiveId>>())
      .awaitSingle()

  suspend fun getIncentive(
    bookingId: Long,
    sequence: Long
  ): NomisIncentive =
    webClient.get()
      .uri("/incentives/booking-id/{bookingId}/incentive-sequence/{sequence}", bookingId, sequence)
      .retrieve()
      .bodyToMono(NomisIncentive::class.java)
      .awaitSingle()

  suspend fun getCurrentIncentive(bookingId: Long): NomisIncentive? =
    webClient.get()
      .uri("/incentives/booking-id/{bookingId}/current", bookingId)
      .retrieve()
      .bodyToMono(NomisIncentive::class.java)
      .onErrorResume(WebClientResponseException::class.java) { emptyWhenNotFound(it, "No current incentive found for bookingId $bookingId") }
      .awaitSingleOrNull()

  suspend fun getSentencingAdjustmentIds(
    fromDate: LocalDate?,
    toDate: LocalDate?,
    pageNumber: Long,
    pageSize: Long
  ): PageImpl<NomisAdjustmentId> =
    webClient.get()
      .uri {
        it.path("/adjustments/ids")
          .queryParam("fromDate", fromDate)
          .queryParam("toDate", toDate)
          .queryParam("page", pageNumber)
          .queryParam("size", pageSize)
          .build()
      }
      .retrieve()
      .bodyToMono(typeReference<RestResponsePage<NomisAdjustmentId>>())
      .awaitSingle()

  suspend fun getSentenceAdjustment(
    nomisSentenceAdjustmentId: Long,
  ): NomisSentenceAdjustment =
    webClient.get()
      .uri("/sentence-adjustments/{nomisSentenceAdjustmentId}", nomisSentenceAdjustmentId)
      .retrieve()
      .bodyToMono(NomisSentenceAdjustment::class.java)
      .awaitSingle()

  fun <T> emptyWhenNotFound(exception: WebClientResponseException, optionalWarnMessage: String? = null): Mono<T> {
    optionalWarnMessage?.let { log.warn(optionalWarnMessage) }
    return emptyWhen(exception, HttpStatus.NOT_FOUND)
  }
  fun <T> emptyWhen(exception: WebClientResponseException, statusCode: HttpStatus): Mono<T> =
    if (exception.statusCode == statusCode) Mono.empty() else Mono.error(exception)
}

data class VisitId(
  val visitId: Long
)

data class IncentiveId(
  val bookingId: Long,
  val sequence: Long,
)

data class NomisAdjustmentId(
  val adjustmentId: Long,
  val adjustmentCategory: String,
)

data class NomisVisitor(
  val personId: Long
)

data class NomisLeadVisitor(
  val personId: Long,
  val fullName: String,
  // latest modified first
  val telephones: List<String>,
)

data class NomisCodeDescription(val code: String, val description: String)

data class NomisVisit(
  val visitId: Long,
  val offenderNo: String,
  val startDateTime: LocalDateTime,
  val endDateTime: LocalDateTime,
  val prisonId: String,
  val visitors: List<NomisVisitor>,
  val visitType: NomisCodeDescription,
  val visitStatus: NomisCodeDescription,
  val visitOutcome: NomisCodeDescription? = null,
  val agencyInternalLocation: NomisCodeDescription? = null,
  val commentText: String? = null,
  val visitorConcernText: String? = null,
  val leadVisitor: NomisLeadVisitor? = null,
  val modifyUserId: String? = null,
  val whenCreated: LocalDateTime,
  val whenUpdated: LocalDateTime? = null
)

data class NomisIncentive(
  val bookingId: Long,
  val incentiveSequence: Long,
  val commentText: String? = null,
  val iepDateTime: LocalDateTime,
  val prisonId: String,
  val iepLevel: NomisCodeDescription,
  val userId: String? = null,
  val offenderNo: String,
  val currentIep: Boolean,
  val whenCreated: LocalDateTime,
  val whenUpdated: LocalDateTime? = null
) {
  fun toIncentive(reviewType: ReviewType): CreateIncentiveIEP = CreateIncentiveIEP(
    iepLevel = iepLevel.code,
    prisonId = this.prisonId,
    iepTime = getTransformedIncentiveDateTime(),
    userId = this.userId,
    comment = this.commentText,
    current = this.currentIep,
    reviewType = reviewType,
  )

  fun toUpdateIncentive(): UpdateIncentiveIEP = UpdateIncentiveIEP(
    iepTime = getTransformedIncentiveDateTime(),
    comment = this.commentText,
    current = this.currentIep,
  )

  /* NOMIS does not persist the seconds portion of the IEP during a manual IEP creation (or update) in pnomis.
     This causes ordering issues within non-current IEPs within the Incentives service which doesn't replicate the nomis incentives seq.
     We will use the seconds from the 'when created' timestamp to avoid multiple IEP records created in the same minute being out of order.
   */
  private fun getTransformedIncentiveDateTime(): LocalDateTime =
    if (iepDateTime.minute != whenCreated.minute) {
      iepDateTime.withSecond(59)
    } else
      iepDateTime.withSecond(whenCreated.second)
}

data class NomisSentenceAdjustment(
  val id: Long,
  val bookingId: Long,
  val sentenceSequence: Long,
  val adjustmentType: String,
  val adjustmentDate: LocalDate,
  val adjustmentFromDate: LocalDate?,
  val adjustmentToDate: LocalDate?,
  val adjustmentDays: Long,
  val comment: String?,
  val active: Boolean,
) {
  fun toSentenceAdjustment(): CreateSentenceAdjustment = CreateSentenceAdjustment(
    bookingId = bookingId,
    sentenceSequence = sentenceSequence,
    adjustmentType = adjustmentType,
    adjustmentDate = adjustmentDate,
    adjustmentFromDate = adjustmentFromDate,
    adjustmentDays = adjustmentDays,
    comment = comment,
    active = active
  )
}

class RestResponsePage<T> @JsonCreator(mode = JsonCreator.Mode.PROPERTIES) constructor(
  @JsonProperty("content") content: List<T>,
  @JsonProperty("number") number: Int,
  @JsonProperty("size") size: Int,
  @JsonProperty("totalElements") totalElements: Long,
  @Suppress("UNUSED_PARAMETER") @JsonProperty(
    "pageable"
  ) pageable: JsonNode
) : PageImpl<T>(content, PageRequest.of(number, size), totalElements)

inline fun <reified T> typeReference() = object : ParameterizedTypeReference<T>() {}
