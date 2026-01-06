package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service

import com.fasterxml.jackson.annotation.JsonProperty
import kotlinx.coroutines.reactive.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.ParameterizedTypeReference
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.web.PagedModel
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.awaitBodilessEntity
import org.springframework.web.reactive.function.client.awaitBody
import reactor.core.publisher.Mono
import tools.jackson.databind.JsonNode
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.appointments.AppointmentIdResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.appointments.AppointmentResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.BadRequestException
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodilessEntityAsTrueNotFoundAsFalse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrNullWhenNotFound
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.api.ProfileDetailsResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.api.ServiceAgencySwitchesResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.EndActivitiesRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.FindActiveActivityIdsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.FindActiveAllocationIdsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.GetActivityResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.GetAllocationResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.LocationResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.MoveActivityEndDateRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerDetails
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerProfileDetailsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visits.VisitRoomUsageResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visits.VisitsMigrationFilter
import java.time.LocalDate
import java.time.LocalDateTime

@Service
class NomisApiService(@Qualifier("nomisApiWebClient") private val webClient: WebClient) {
  private val profileDetailsResourceApi = ProfileDetailsResourceApi(webClient)
  private val serviceAgencySwitchesResourceApi = ServiceAgencySwitchesResourceApi(webClient)

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
        .apply {
          prisonIds.forEach { queryParam("prisonIds", it) }
          visitTypes.forEach { queryParam("visitTypes", it) }
          fromDateTime?.let { queryParam("fromDateTime", it) }
          toDateTime?.let { queryParam("toDateTime", it) }
        }
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
        .apply {
          filter.prisonIds.forEach { queryParam("prisonIds", it) }
          filter.visitTypes.forEach { queryParam("visitTypes", it) }
          filter.fromDateTime?.let { queryParam("fromDateTime", it) }
          filter.toDateTime?.let { queryParam("toDateTime", it) }
        }
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
        .apply {
          prisonIds.forEach { queryParam("prisonIds", it) }
          fromDate?.let { queryParam("fromDate", it) }
          toDate?.let { queryParam("toDate", it) }
        }
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
        .apply { courseActivityId?.let { queryParam("courseActivityId", courseActivityId) } }
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

  suspend fun moveActivityEndDates(ids: List<Long>, oldEndDate: LocalDate, newEndDate: LocalDate) = webClient.put()
    .uri("/activities/move-end-date")
    .body(BodyInserters.fromValue(MoveActivityEndDateRequest(ids, oldEndDate, newEndDate)))
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
    activeOnDate: LocalDate? = null,
    pageNumber: Long,
    pageSize: Long,
  ): PageImpl<FindActiveAllocationIdsResponse> = webClient.get()
    .uri {
      it.path("/allocations/ids")
        .queryParam("prisonId", prisonId)
        .apply {
          courseActivityId?.let { queryParam("courseActivityId", courseActivityId) }
          activeOnDate?.let { queryParam("activeOnDate", activeOnDate) }
        }
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

  suspend fun getPrisonerDetails(offenderNo: String): PrisonerDetails? = webClient.get()
    .uri("/prisoners/{offenderNo}", offenderNo)
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun getProfileDetails(offenderNo: String, profileTypes: List<String> = emptyList(), bookingId: Long? = null): PrisonerProfileDetailsResponse = profileDetailsResourceApi
    .getProfileDetails(offenderNo, profileTypes, bookingId)
    .awaitSingle()

  suspend fun isServiceAgencyOnForPrisoner(serviceCode: String, prisonNumber: String) = serviceAgencySwitchesResourceApi
    .prepare(serviceAgencySwitchesResourceApi.checkServicePrisonForPrisonerRequestConfig(serviceCode, prisonNumber))
    .retrieve()
    .awaitBodilessEntityAsTrueNotFoundAsFalse()

  suspend fun isAgencySwitchOnForAgency(serviceCode: String, agencyId: String) = serviceAgencySwitchesResourceApi
    .prepare(serviceAgencySwitchesResourceApi.checkServiceAgencyRequestConfig(serviceCode, agencyId))
    .retrieve()
    .awaitBodilessEntityAsTrueNotFoundAsFalse()
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

class RestResponsePage<T : Any>(
  @JsonProperty("content") content: List<T>,
  @JsonProperty("number") number: Int,
  @JsonProperty("size") size: Int,
  @JsonProperty("totalElements") totalElements: Long,
  @Suppress("UNUSED_PARAMETER")
  @JsonProperty("pageable") pageable: JsonNode,
) : PageImpl<T>(content, PageRequest.of(number, size), totalElements)

data class PageMetadata(
  val size: Int,
  val number: Int,
  val totalElements: Long,
  val totalPages: Int,
)

class RestResponsePagedModel<T : Any>(
  @JsonProperty("content") content: List<T>,
  @JsonProperty("page") page: PageMetadata,
) : PagedModel<T>(
  PageImpl(content, PageRequest.of(page.number.toInt(), page.size.toInt()), page.totalElements),
)

inline fun <reified T : Any> typeReference() = object : ParameterizedTypeReference<T>() {}
