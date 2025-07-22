package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrNullWhenNotFound
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.MigrationMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TransactionMappingDto

@Service
class TransactionMappingApiService(@Qualifier("mappingApiWebClient") webClient: WebClient) : MigrationMapping<TransactionMappingDto>(domainUrl = "/mapping/transactions", webClient) {
  suspend fun getMappingGivenNomisIdOrNull(transactionId: Long): TransactionMappingDto? = webClient.get()
    .uri("/mapping/transactions/nomis-transaction-id/{transactionId}", transactionId)
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

// TODO suspend fun updateMappingsByNomisId(oldOffenderNo: String, newOffenderNo: String) {
//    webClient.put()
//      .uri("/mapping/transactions/merge/from/{oldOffenderNo}/to/{newOffenderNo}", oldOffenderNo, newOffenderNo)
//      .retrieve()
//      .awaitBodilessEntity()
//  }
//
//  suspend fun updateMappingsByBookingId(bookingId: Long, newOffenderNo: String): List<TransactionMappingDto> =
//    webClient.put()
//      .uri("/mapping/transactions/merge/booking-id/{bookingId}/to/{newOffenderNo}", bookingId, newOffenderNo)
//      .retrieve()
//      .awaitBody()
}
