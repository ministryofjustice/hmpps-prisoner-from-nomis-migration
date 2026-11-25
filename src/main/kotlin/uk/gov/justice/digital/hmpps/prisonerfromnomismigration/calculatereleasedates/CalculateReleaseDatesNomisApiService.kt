package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.calculatereleasedates

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.api.CalculateReleaseDatesResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderSentenceCalculationResponse

@Service
class CalculateReleaseDatesNomisApiService(@Qualifier("nomisApiWebClient") webClient: WebClient) {
  private val api = CalculateReleaseDatesResourceApi(webClient)

  suspend fun getSentenceCalculation(offenderSentenceCalculationId: Long): OffenderSentenceCalculationResponse = api
    .getOffenderSentenceCalculation(offenderSentenceCalculationId)
    .awaitSingle()

  suspend fun getSentenceCalculationsForPrisoner(offenderNo: String): List<OffenderSentenceCalculationResponse> = api
    .getOffenderSentenceCalculations(offenderNo)
    .awaitSingle()
}
