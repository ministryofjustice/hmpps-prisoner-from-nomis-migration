package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incentives

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonInclude
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import java.time.LocalDateTime

@Service
class IncentivesService(@Qualifier("incentivesApiWebClient") private val webClient: WebClient) {
  suspend fun migrateIncentive(incentive: CreateIncentiveIEP, bookingId: Long): CreateIncentiveIEPResponse =
    webClient.post()
      .uri("/iep/migration/booking/{bookingId}", bookingId)
      .bodyValue(incentive)
      .retrieve()
      .bodyToMono(CreateIncentiveIEPResponse::class.java)
      .awaitSingle()

  suspend fun synchroniseCreateIncentive(incentive: CreateIncentiveIEP, bookingId: Long): CreateIncentiveIEPResponse =
    webClient.post()
      .uri("/iep/sync/booking/{bookingId}", bookingId)
      .bodyValue(incentive)
      .retrieve()
      .bodyToMono(CreateIncentiveIEPResponse::class.java)
      .awaitSingle()

  suspend fun synchroniseUpdateIncentive(bookingId: Long, incentiveId: Long, incentive: UpdateIncentiveIEP) =
    webClient.patch()
      .uri("/iep/sync/booking/{bookingId}/id/{id}", bookingId, incentiveId)
      .bodyValue(incentive)
      .retrieve()
      .bodyToMono(Unit::class.java)
      .awaitSingleOrNull()

  suspend fun synchroniseDeleteIncentive(bookingId: Long, incentiveId: Long) =
    webClient.delete()
      .uri("/iep/sync/booking/{bookingId}/id/{id}", bookingId, incentiveId)
      .retrieve()
      .bodyToMono(Unit::class.java)
      .awaitSingleOrNull()
}

@JsonInclude(JsonInclude.Include.NON_NULL)
data class CreateIncentiveIEP(
  val prisonId: String,
  @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
  val iepTime: LocalDateTime,
  val userId: String?,
  val iepLevel: String,
  val comment: String? = null,
  val current: Boolean,
  val reviewType: ReviewType,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class UpdateIncentiveIEP(
  @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
  val iepTime: LocalDateTime,
  val comment: String? = null,
  val current: Boolean,
)

enum class ReviewType {
  REVIEW, MIGRATED
}

data class CreateIncentiveIEPResponse(
  val id: Long,
)
