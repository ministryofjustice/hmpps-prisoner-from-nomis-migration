package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.scheduledpayments

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitSingleOrNullForNotFound
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.api.ScheduledPaymentsResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.ScheduledPaymentDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.ScheduledPaymentsCount

@Service
class ScheduledPaymentsNomisApiService(@Qualifier("nomisApiWebClient") private val webClient: WebClient) {
  private val api = ScheduledPaymentsResourceApi(webClient)

  suspend fun getScheduledPayment(scheduledPaymentId: Long): ScheduledPaymentDto = api
    .getScheduledPayment(scheduledPaymentId)
    .awaitSingle()

  suspend fun getScheduledPaymentOrNull(advanceId: Long): ScheduledPaymentDto? = api
    .getScheduledPayment(advanceId)
    .awaitSingleOrNullForNotFound()

  suspend fun getActivePrisonerScheduledPayments(rootOffenderId: Long): List<ScheduledPaymentDto> = api
    .getPrisonerScheduledPaymentsById(rootOffenderId = rootOffenderId, activeOnly = true)
    .awaitSingle()

  suspend fun getActiveScheduledPaymentsCount(): ScheduledPaymentsCount = api.getActiveScheduledPaymentsCount()
    .awaitSingle()
}

data class ScheduledPaymentCount(
  val activeCount: Long,

)
