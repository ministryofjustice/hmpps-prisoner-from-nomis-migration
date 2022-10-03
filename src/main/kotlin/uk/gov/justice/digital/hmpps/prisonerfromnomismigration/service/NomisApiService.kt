package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.JsonNode
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.runBlocking
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.ParameterizedTypeReference
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incentives.CreateIncentiveIEP
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incentives.ReviewType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visits.VisitRoomUsageResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visits.VisitsMigrationFilter
import java.time.LocalDate
import java.time.LocalDateTime

@Service
class NomisApiService(@Qualifier("nomisApiWebClient") private val webClient: WebClient) {

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

  fun getVisitsBlocking(
    prisonIds: List<String>,
    visitTypes: List<String>,
    fromDateTime: LocalDateTime?,
    toDateTime: LocalDateTime?,
    ignoreMissingRoom: Boolean,
    pageNumber: Long,
    pageSize: Long,
  ): PageImpl<VisitId> = runBlocking {
    getVisits(
      prisonIds,
      visitTypes,
      fromDateTime,
      toDateTime,
      ignoreMissingRoom,
      pageNumber,
      pageSize,
    )
  }

  fun getVisit(
    nomisVisitId: Long,
  ): NomisVisit =
    webClient.get()
      .uri("/visits/{nomisVisitId}", nomisVisitId)
      .retrieve()
      .bodyToMono(NomisVisit::class.java)
      .block()!!

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

  fun getIncentivesBlocking(
    fromDate: LocalDate?,
    toDate: LocalDate?,
    pageNumber: Long,
    pageSize: Long
  ): PageImpl<IncentiveId> = runBlocking {
    getIncentives(
      fromDate,
      toDate,
      pageNumber,
      pageSize,
    )
  }

  suspend fun getIncentive(
    bookingId: Long,
    sequence: Long
  ): NomisIncentive =
    webClient.get()
      .uri("/incentives/booking-id/{bookingId}/incentive-sequence/{sequence}", bookingId, sequence)
      .retrieve()
      .bodyToMono(NomisIncentive::class.java)
      .awaitSingle()

  fun getIncentiveBlocking(
    bookingId: Long,
    sequence: Long
  ): NomisIncentive = runBlocking {
    getIncentive(
      bookingId,
      sequence,
    )
  }

  suspend fun getCurrentIncentive(bookingId: Long): NomisIncentive =
    webClient.get()
      .uri("/incentives/booking-id/{bookingId}/current", bookingId)
      .retrieve()
      .bodyToMono(NomisIncentive::class.java)
      .awaitSingle()
}

data class VisitId(
  val visitId: Long
)

data class IncentiveId(
  val bookingId: Long,
  val sequence: Long,
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
  val modifyUserId: String? = null
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
  val currentIep: Boolean
) {
  fun toIncentive(reviewType: ReviewType): CreateIncentiveIEP = CreateIncentiveIEP(
    iepLevel = iepLevel.code,
    locationId = "${this.prisonId}-RECP", // todo wire up when nomis api amended
    prisonId = this.prisonId,
    iepTime = this.iepDateTime,
    userId = this.userId,
    comment = this.commentText,
    current = this.currentIep,
    reviewType = reviewType,
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
