package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incentives

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonInclude
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
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
}

@JsonInclude(JsonInclude.Include.NON_NULL)
data class CreateIncentiveIEP(
  val locationId: String,
  val bookingId: Long,
  val prisonerNumber: String,
  @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
  val reviewTime: LocalDateTime,
  val reviewedBy: String,
  val iepCode: String,
  val commentText: String? = null,
  val current: Boolean,
  val reviewType: ReviewType,
)

enum class ReviewType {
  INITIAL, REVIEW, TRANSFER, ADJUSTMENT
}

data class CreateIncentiveIEPResponse(
  val id: Long,
)
