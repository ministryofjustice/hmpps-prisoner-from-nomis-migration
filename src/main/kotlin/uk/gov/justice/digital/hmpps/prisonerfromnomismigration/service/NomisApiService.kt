package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.JsonNode
import kotlinx.coroutines.reactive.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.ParameterizedTypeReference
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.awaitBodilessEntity
import org.springframework.web.reactive.function.client.awaitBody
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities.model.AppointmentMigrateRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.BadRequestException
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.EndActivitiesRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.FindActiveActivityIdsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.FindActiveAllocationIdsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.GetActivityResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.GetAllocationResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.LocationIdResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.LocationResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visits.VisitRoomUsageResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visits.VisitsMigrationFilter
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

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
  ): PageImpl<VisitId> = webClient.get()
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
  ): NomisVisit = webClient.get()
    .uri("/visits/{nomisVisitId}", nomisVisitId)
    .retrieve()
    .bodyToMono(NomisVisit::class.java)
    .awaitSingle()!!

  suspend fun getRoomUsage(
    filter: VisitsMigrationFilter,
  ): List<VisitRoomUsageResponse> = webClient.get()
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

  suspend fun getAppointment(nomisEventId: Long): AppointmentResponse = webClient.get()
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
  ): PageImpl<AppointmentIdResponse> = webClient.get()
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

  suspend fun getActivity(courseActivityId: Long): GetActivityResponse = webClient.get()
    .uri("/activities/{courseActivityId}", courseActivityId)
    .retrieve()
    .bodyToMono(GetActivityResponse::class.java)
    .awaitSingle()

  suspend fun getActivityIds(
    prisonId: String,
    courseActivityId: Long? = null,
    pageNumber: Long,
    pageSize: Long,
  ): PageImpl<FindActiveActivityIdsResponse> = webClient.get()
    .uri {
      it.path("/activities/ids")
        .queryParam("prisonId", prisonId)
        .apply { courseActivityId?.run { queryParam("courseActivityId", courseActivityId) } }
        .queryParam("page", pageNumber)
        .queryParam("size", pageSize)
        .build()
    }
    .retrieve()
    .bodyToMono(typeReference<RestResponsePage<FindActiveActivityIdsResponse>>())
    .onErrorResume(WebClientResponseException.BadRequest::class.java) {
      val errorResponse = it.getResponseBodyAs(ErrorResponse::class.java) as ErrorResponse
      Mono.error(BadRequestException(errorResponse.userMessage ?: "Received a 400 calling /activities/ids"))
    }
    .awaitSingle()

  suspend fun endActivities(ids: List<Long>, endDate: LocalDate) = webClient.put()
    .uri("/activities/end")
    .body(BodyInserters.fromValue(EndActivitiesRequest(ids, endDate)))
    .retrieve()
    .awaitBodilessEntity()

  suspend fun getAllocation(allocationId: Long): GetAllocationResponse = webClient.get()
    .uri("/allocations/{allocationId}", allocationId)
    .retrieve()
    .bodyToMono(GetAllocationResponse::class.java)
    .awaitSingle()

  suspend fun getAllocationIds(
    prisonId: String,
    courseActivityId: Long? = null,
    pageNumber: Long,
    pageSize: Long,
  ): PageImpl<FindActiveAllocationIdsResponse> = webClient.get()
    .uri {
      it.path("/allocations/ids")
        .queryParam("prisonId", prisonId)
        .apply { courseActivityId?.run { queryParam("courseActivityId", courseActivityId) } }
        .queryParam("page", pageNumber)
        .queryParam("size", pageSize)
        .build()
    }
    .retrieve()
    .bodyToMono(typeReference<RestResponsePage<FindActiveAllocationIdsResponse>>())
    .onErrorResume(WebClientResponseException.BadRequest::class.java) {
      val errorResponse = it.getResponseBodyAs(ErrorResponse::class.java) as ErrorResponse
      Mono.error(BadRequestException(errorResponse.userMessage ?: "Received a 400 calling /allocations/ids"))
    }
    .awaitSingle()

  // /////////////////////////////////////// Locations

  suspend fun getLocation(locationId: Long): LocationResponse = webClient.get()
    .uri("/locations/{locationId}", locationId)
    .retrieve()
    .bodyToMono(LocationResponse::class.java)
    .awaitSingle()

  suspend fun getLocationIds(
    pageNumber: Long,
    pageSize: Long,
  ): PageImpl<LocationIdResponse> = webClient.get()
    .uri {
      it.path("/locations/ids")
        .queryParam("page", pageNumber)
        .queryParam("size", pageSize)
        .build()
    }
    .retrieve()
    .bodyToMono(typeReference<RestResponsePage<LocationIdResponse>>())
    .awaitSingle()

  // /////////////////////////////////////// General

  suspend fun getPrisonerIds(pageNumber: Long, pageSize: Long): RestResponsePage<PrisonerId> = webClient.get()
    .uri {
      it.path("/prisoners/ids/all")
        .queryParam("page", pageNumber)
        .queryParam("size", pageSize)
        .build()
    }
    .retrieve()
    .awaitBody()
}

data class VisitId(
  val visitId: Long,
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

private val simpleTimeFormat = DateTimeFormatter.ofPattern("HH:mm")

data class AppointmentResponse(
  val bookingId: Long,
  val offenderNo: String,
  // prison or toPrison is never null in existing nomis data for event_type = 'APP' (as at 11/5/2023)
  val prisonId: String,
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
    // startDate never null in existing nomis data for event_type = 'APP' (as at 11/5/2023)
    startDate = startDateTime!!.toLocalDate(),
    startTime = startDateTime.toLocalTime().format(simpleTimeFormat),
    endTime = endDateTime?.toLocalTime()?.format(simpleTimeFormat),
    comment = comment,
    categoryCode = subtype,
    isCancelled = status == "CANC",
    created = createdDate.truncatedTo(ChronoUnit.SECONDS),
    createdBy = createdBy,
    updated = modifiedDate?.truncatedTo(ChronoUnit.SECONDS),
    updatedBy = modifiedBy,
  )
}

data class AppointmentIdResponse(
  val eventId: Long,
)

class RestResponsePage<T>(
  @JsonProperty("content") content: List<T>,
  @JsonProperty("number") number: Int,
  @JsonProperty("size") size: Int,
  @JsonProperty("totalElements") totalElements: Long,
  @Suppress("UNUSED_PARAMETER")
  @JsonProperty("pageable") pageable: JsonNode,
) : PageImpl<T>(content, PageRequest.of(number, size), totalElements)

inline fun <reified T> typeReference() = object : ParameterizedTypeReference<T>() {}
