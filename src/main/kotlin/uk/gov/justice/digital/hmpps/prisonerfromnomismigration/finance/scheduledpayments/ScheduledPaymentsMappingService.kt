package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.scheduledpayments

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitSingleOrNullForNotFound
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.MigrationMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.api.ScheduledPaymentMappingResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ScheduledPaymentMappingDto

@Service
class ScheduledPaymentsMappingService(@Qualifier("mappingApiWebClient") webClient: WebClient) : MigrationMapping<ScheduledPaymentMappingDto>("/mapping/scheduled-payments", webClient) {
  private val api = ScheduledPaymentMappingResourceApi(webClient)

  suspend fun getByNomisIdOrNull(nomisId: Long): ScheduledPaymentMappingDto? = api.getScheduledPaymentMappingByNomisId(nomisId)
    .awaitSingleOrNullForNotFound()

  suspend fun getByNomisId(nomisId: Long): ScheduledPaymentMappingDto = api.getScheduledPaymentMappingByNomisId(nomisId)
    .awaitSingle()
}
