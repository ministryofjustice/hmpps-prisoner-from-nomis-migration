package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrNullWhenNotFound
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.MigrationMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.api.TransactionMappingResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TransactionMappingDto

@Service
class TransactionMappingApiService(@Qualifier("mappingApiWebClient") webClient: WebClient) : MigrationMapping<TransactionMappingDto>(domainUrl = "/mapping/transactions", webClient) {
  private val api = TransactionMappingResourceApi(webClient)

  suspend fun getMappingGivenNomisIdOrNull(transactionId: Long): TransactionMappingDto? = api
    .prepare(api.getMappingByNomisId2RequestConfig(transactionId))
    .retrieve()
    .awaitBodyOrNullWhenNotFound()
}
