package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incentives

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonInclude
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisIncentive
import java.time.LocalDateTime

@Service
class IncentivesService(@Qualifier("incentivesApiWebClient") private val webClient: WebClient) {
  fun migrateIncentive(incentive: CreateIncentiveIEP): CreateIncentiveIEPResponse =
    webClient.post()
      .uri("/iep/migration/booking/{bookingId}", incentive.bookingId)
      .bodyValue(incentive)
      .retrieve()
      .bodyToMono(CreateIncentiveIEPResponse::class.java)
      .block()!!

  suspend fun synchroniseCreateIncentive(incentive: CreateIncentiveIEP): CreateIncentiveIEPResponse =
    webClient.post()
      .uri("/iep/sync/booking/{bookingId}", incentive.bookingId)
      .bodyValue(incentive)
      .retrieve()
      .bodyToMono(CreateIncentiveIEPResponse::class.java)
      .awaitSingle()

  suspend fun synchroniseUpdateIncentive(bookingId: Long, incentiveId: Long, incentive: UpdateIncentiveIEP) =
    webClient.put()
      .uri("/iep/sync/booking/{bookingId}/id/{id}", bookingId, incentiveId)
      .bodyValue(incentive)
      .retrieve()
      .bodyToMono(Unit::class.java)
      .awaitSingleOrNull()
}

@JsonInclude(JsonInclude.Include.NON_NULL)
data class CreateIncentiveIEP(
  val locationId: String,
  val bookingId: Long,
  val prisonerNumber: String,
  @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
  val reviewTime: LocalDateTime,
  val reviewedBy: String?,
  val iepCode: String,
  val commentText: String? = null,
  val current: Boolean,
  val reviewType: ReviewType,
) {
  fun toIncentive(nomisIncentive: NomisIncentive, reviewType: ReviewType): CreateIncentiveIEP = CreateIncentiveIEP(
    bookingId = bookingId,
    prisonerNumber = nomisIncentive.offenderNo,
    iepCode = nomisIncentive.iepLevel.code,
    locationId = nomisIncentive.prisonId,
    reviewTime = nomisIncentive.iepDateTime,
    reviewedBy = nomisIncentive.userId ?: "anonymous", // TODO can this ever happen??
    commentText = nomisIncentive.commentText,
    current = nomisIncentive.currentIep,
    reviewType = reviewType
  )
}

@JsonInclude(JsonInclude.Include.NON_NULL)
data class UpdateIncentiveIEP(
  @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
  val reviewTime: LocalDateTime,
  val commentText: String? = null,
  val current: Boolean,
) {
  fun toIncentive(nomisIncentive: NomisIncentive): UpdateIncentiveIEP = UpdateIncentiveIEP(
    reviewTime = nomisIncentive.iepDateTime,
    commentText = nomisIncentive.commentText,
    current = nomisIncentive.currentIep,
  )
}

enum class ReviewType {
  REVIEW, MIGRATION
}

data class CreateIncentiveIEPResponse(
  val id: Long,
)
