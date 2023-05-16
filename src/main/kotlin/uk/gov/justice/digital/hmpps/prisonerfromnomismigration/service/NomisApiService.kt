package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.JsonNode
import kotlinx.coroutines.reactive.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.ParameterizedTypeReference
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities.model.AppointmentMigrateRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing.SentencingAdjustment
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

  suspend fun getVisit(
    nomisVisitId: Long,
  ): NomisVisit =
    webClient.get()
      .uri("/visits/{nomisVisitId}", nomisVisitId)
      .retrieve()
      .bodyToMono(NomisVisit::class.java)
      .awaitSingle()!!

  suspend fun getRoomUsage(
    filter: VisitsMigrationFilter,
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

  suspend fun getSentencingAdjustmentIds(
    fromDate: LocalDate?,
    toDate: LocalDate?,
    pageNumber: Long,
    pageSize: Long,
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
  ): NomisAdjustment =
    webClient.get()
      .uri("/sentence-adjustments/{nomisSentenceAdjustmentId}", nomisSentenceAdjustmentId)
      .retrieve()
      .bodyToMono(NomisAdjustment::class.java)
      .awaitSingle()

  suspend fun getKeyDateAdjustment(
    nomisKeyDateAdjustmentId: Long,
  ): NomisAdjustment =
    webClient.get()
      .uri("/key-date-adjustments/{nomisKeyDateAdjustmentId}", nomisKeyDateAdjustmentId)
      .retrieve()
      .bodyToMono(NomisAdjustment::class.java)
      .awaitSingle()

  suspend fun getAppointment(nomisEventId: Long): AppointmentResponse =
    webClient.get()
      .uri("/appointments/{nomisEventId}", nomisEventId)
      .retrieve()
      .bodyToMono(AppointmentResponse::class.java)
      .awaitSingle()

  suspend fun getAppointmentIds(
    prisonIds: List<String>,
    fromDate: LocalDate?,
    toDate: LocalDate?,
    pageNumber: Long,
    pageSize: Long,
  ): PageImpl<AppointmentIdResponse> =
    webClient.get()
      .uri {
        it.path("/appointments/ids")
          .queryParam("prisonIds", prisonIds)
          .queryParam("fromDate", fromDate)
          .queryParam("toDate", toDate)
          .queryParam("page", pageNumber)
          .queryParam("size", pageSize)
          .build()
      }
      .retrieve()
      .bodyToMono(typeReference<RestResponsePage<AppointmentIdResponse>>())
      .awaitSingle()
}

data class VisitId(
  val visitId: Long,
)

data class NomisAdjustmentId(
  val adjustmentId: Long,
  val adjustmentCategory: String,
)

data class NomisVisitor(
  val personId: Long,
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
  val createUserId: String,
  val modifyUserId: String? = null,
  val whenCreated: LocalDateTime,
  val whenUpdated: LocalDateTime? = null,
)

data class NomisAdjustment(
  val id: Long,
  val bookingId: Long,
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
) {
  fun toSentencingAdjustment(): SentencingAdjustment = SentencingAdjustment(
    bookingId = bookingId,
    sentenceSequence = sentenceSequence,
    adjustmentType = adjustmentType.code,
    adjustmentDate = adjustmentDate,
    adjustmentFromDate = adjustmentFromDate,
    adjustmentDays = adjustmentDays,
    comment = comment,
    active = active,
    offenderNo = offenderNo,
  )

  fun getAdjustmentCategory() = sentenceSequence?.let { "SENTENCE" } ?: "KEY_DATE"
}

data class AppointmentResponse(
  val bookingId: Long,
  val offenderNo: String,
  val prisonId: String, // prison or toPrison is never null in existing nomis data for event_type = 'APP' (as at 11/5/2023)
  val internalLocation: Long? = null,
  val startDateTime: LocalDateTime? = null,
  val endDateTime: LocalDateTime? = null,
  val comment: String? = null,
  val subtype: String,
  val status: String,
  val createdDate: LocalDateTime,
  val createdBy: String,
  val modifiedDate: LocalDateTime? = null,
  val modifiedBy: String? = null,
) {
  fun toAppointment() = AppointmentMigrateRequest(
    bookingId = bookingId,
    prisonerNumber = offenderNo,
    prisonCode = prisonId,
    internalLocationId = internalLocation!!,
    startDate = startDateTime!!.toLocalDate().toString(), // never null in existing nomis data for event_type = 'APP' (as at 11/5/2023)
    startTime = startDateTime.toLocalTime().toString(),
    endTime = endDateTime?.toLocalTime().toString(),
    comment = comment,
    categoryCode = subtype,
    isCancelled = status == "CANC",
    created = createdDate.toString(),
    createdBy = createdBy,
    updated = modifiedDate?.toString(),
    updatedBy = modifiedBy,
  )
}

data class AppointmentIdResponse(
  val eventId: Long,
)

class RestResponsePage<T>
@JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
constructor(
  @JsonProperty("content") content: List<T>,
  @JsonProperty("number") number: Int,
  @JsonProperty("size") size: Int,
  @JsonProperty("totalElements") totalElements: Long,
  @Suppress("UNUSED_PARAMETER")
  @JsonProperty(
    "pageable",
  )
  pageable: JsonNode,
) : PageImpl<T>(content, PageRequest.of(number, size), totalElements)

inline fun <reified T> typeReference() = object : ParameterizedTypeReference<T>() {}
