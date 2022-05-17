package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visits

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonInclude
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import java.time.LocalDateTime

@Service
class VisitsService(@Qualifier("visitsApiWebClient") private val webClient: WebClient) {

  fun createVisit(createVisitRequest: CreateVsipVisit): VsipVisit =
    webClient.post()
      .uri("/visits")
      .bodyValue(createVisitRequest)
      .retrieve()
      .bodyToMono(VsipVisit::class.java)
      .block()!!
}

@JsonInclude(JsonInclude.Include.NON_NULL)
data class CreateVsipVisit(
  val prisonerId: String,
  val prisonId: String,
  @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
  val startTimestamp: LocalDateTime,
  @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
  val endTimestamp: LocalDateTime,
  val visitType: String,
  val visitStatus: VsipStatus,
  val outcomeStatus: VsipOutcome? = null,
  val visitRoom: String,
  val reasonableAdjustments: String? = null,
  val contactList: List<VsipVisitor>? = listOf(),
  val sessionId: Long? = null,
)

data class VsipVisitor(
  val nomisPersonId: Long,
  val leadVisitor: Boolean = false
)

data class VsipVisit(val visitId: String)
