package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.locations

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBodilessEntity
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrLogAndRethrowError
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.locations.model.LegacyLocation
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.locations.model.NomisSyncLocationRequest

@Service
class LocationsService(@Qualifier("locationsApiWebClient") private val webClient: WebClient) {
  suspend fun upsertLocation(upsertRequest: NomisSyncLocationRequest): LegacyLocation = webClient.post()
    .uri("/sync/upsert")
    .bodyValue(upsertRequest)
    .retrieve()
    .awaitBodyOrLogAndRethrowError()

  suspend fun deleteLocation(id: String) {
    webClient.delete()
      .uri("/sync/delete/{id}", id)
      .retrieve()
      .awaitBodilessEntity()
  }
}
