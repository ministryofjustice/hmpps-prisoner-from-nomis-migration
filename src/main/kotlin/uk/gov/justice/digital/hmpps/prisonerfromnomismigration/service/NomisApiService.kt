package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.JsonNode
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.ParameterizedTypeReference
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visits.VisitRoomUsageResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visits.VisitsMigrationFilter
import java.time.LocalDateTime

@Service
class NomisApiService(@Qualifier("nomisApiWebClient") private val webClient: WebClient) {

  fun getVisits(
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
      .block()!!

  fun getVisit(
    nomisVisitId: Long,
  ): NomisVisit =
    webClient.get()
      .uri("/visits/{nomisVisitId}", nomisVisitId)
      .retrieve()
      .bodyToMono(NomisVisit::class.java)
      .block()!!

  fun getRoomUsage(filter: VisitsMigrationFilter
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
      .block()!!
}

data class VisitId(
  val visitId: Long
)

data class NomisVisitor(
  val personId: Long,
  val leadVisitor: Boolean
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
  val agencyInternalLocation: NomisCodeDescription? = null,
  val commentText: String? = null,
  val visitorConcernText: String? = null,
)

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
