package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visits

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonInclude
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import java.time.LocalDateTime

@Service
class VisitsService(@Qualifier("visitsApiWebClient") private val webClient: WebClient) {

  fun createVisit(createVisitRequest: CreateVsipVisit): String =
    webClient.post()
      .uri("/migrate-visits")
      .bodyValue(createVisitRequest)
      .retrieve()
      .bodyToMono(String::class.java)
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
  val visitRoom: String?,
  val reasonableAdjustments: String? = null,
  val contactList: List<VsipVisitor>? = listOf(),
  val sessionId: Long? = null,
  val legacyData: VsipLegacyData? = null,
  val visitContact: VsipLegacyContactOnVisit? = null,
  val visitors: Set<VsipVisitor>? = setOf(),
  val visitNotes: Set<VsipVisitNote>? = setOf(),
  val visitRestriction: VisitRestriction
)

data class VsipLegacyData(
  val leadVisitorId: Long
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class VsipLegacyContactOnVisit(
  val name: String,
  val telephone: String? = null,
)

data class VsipVisitor(
  val nomisPersonId: Long
)

class VsipVisitNote(
  val type: VsipVisitNoteType,
  val text: String
)

enum class VsipVisitNoteType {
  VISITOR_CONCERN,
  VISIT_COMMENT,
}

enum class VisitRestriction(
  val description: String,
) {
  OPEN("Open"),
  CLOSED("Closed"),
  UNKNOWN("Unknown")
}
