package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.locations

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBodilessEntity
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.locations.model.LegacyLocation
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.locations.model.Location
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.locations.model.NomisMigrateLocationRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.locations.model.NomisSyncLocationRequest

@Service
class LocationsService(@Qualifier("locationsApiWebClient") private val webClient: WebClient) {
  suspend fun upsertLocation(upsertRequest: NomisSyncLocationRequest): LegacyLocation =
    webClient.post()
      .uri("/sync/upsert")
      .bodyValue(upsertRequest)
      .retrieve()
      .awaitBody()

  suspend fun deleteLocation(id: String) {
    webClient.delete()
      .uri("/sync/delete/{id}", id)
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun migrateLocation(request: NomisMigrateLocationRequest): Location =
    webClient.post()
      .uri("/migrate/location")
      .bodyValue(request)
      .retrieve()
      .awaitBody()
}
