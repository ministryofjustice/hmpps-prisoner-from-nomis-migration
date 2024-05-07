package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBodilessEntity
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrNullWhenNotFound
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.MigrationMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.AlertMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.NomisMappingIdUpdate

@Service
class AlertsMappingApiService(@Qualifier("mappingApiWebClient") webClient: WebClient) :
  MigrationMapping<AlertMappingDto>(domainUrl = "/mapping/alerts", webClient) {
  suspend fun getOrNullByNomisId(bookingId: Long, alertSequence: Long): AlertMappingDto? = webClient.get()
    .uri(
      "/mapping/alerts/nomis-booking-id/{bookingId}/nomis-alert-sequence/{alertSequence}",
      bookingId,
      alertSequence,
    )
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun createMapping(alertMappingDto: AlertMappingDto) {
    webClient.post()
      .uri("/mapping/alerts")
      .bodyValue(alertMappingDto)
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun createMappingsBatch(mappings: List<AlertMappingDto>) {
    webClient.post()
      .uri("/mapping/alerts/batch")
      .bodyValue(mappings)
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun deleteMappingByDpsId(dpsAlertId: String) {
    webClient.delete()
      .uri("/mapping/alerts/dps-alert-id/{alertId}", dpsAlertId)
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun updateNomisMappingId(previousBookingId: Long, alertSequence: Long, newBookingId: Long): AlertMappingDto? =
    webClient.put()
      .uri(
        "/mapping/alerts/nomis-booking-id/{bookingId}/nomis-alert-sequence/{alertSequence}",
        previousBookingId,
        alertSequence,
      )
      .bodyValue(NomisMappingIdUpdate(bookingId = newBookingId))
      .retrieve()
      .awaitBodyOrNullWhenNotFound()
}
