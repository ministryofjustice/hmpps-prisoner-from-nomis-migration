package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csra

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody

@Service
class CsraApiService(@Qualifier("csraApiWebClient") private val webClient: WebClient) {

  suspend fun migrateCsras(offenderNo: String, dpsCsras: List<CsraReviewDto>): List<MigrationResult> = webClient
    .post()
    .uri("/csras/migrate/{offenderNo}", offenderNo)
    .contentType(MediaType.APPLICATION_JSON)
    .bodyValue(dpsCsras)
    .retrieve()
    .awaitBody()
}

// TEMPORARY until real api is available
data class MigrationResult(
  val dpsCsraId: kotlin.String,
  val nomisBookingId: kotlin.Long,
  val nomisSequence: kotlin.Int,
)
