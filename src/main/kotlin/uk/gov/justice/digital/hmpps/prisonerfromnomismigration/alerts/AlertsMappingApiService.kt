package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrNullWhenNotFound
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.AlertMappingDto

@Service
class AlertsMappingApiService(@Qualifier("mappingApiWebClient") private val webClient: WebClient) {
  suspend fun getOrNullByNomisId(bookingId: Long, alertSequence: Long): AlertMappingDto? = webClient.get()
    .uri(
      "/mapping/alerts/nomis-booking-id/{bookingId}/nomis-alert-sequence/{alertSequence}",
      bookingId,
      alertSequence,
    )
    .retrieve()
    .awaitBodyOrNullWhenNotFound()
}
