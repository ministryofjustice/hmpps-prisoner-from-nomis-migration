package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.locations

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import org.springframework.web.reactive.function.client.awaitBodyOrNull
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.locations.model.ChangeHistory
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.locations.model.Location
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.locations.model.MigrateHistoryRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.locations.model.UpsertLocationRequest
import java.util.*

@Service
class LocationsService(@Qualifier("locationsApiWebClient") private val webClient: WebClient) {
  suspend fun upsertLocation(upsertRequest: UpsertLocationRequest): Location =
    webClient.post()
      .uri("/sync/upsert")
      .bodyValue(upsertRequest)
      .retrieve()
      .awaitBody()

  suspend fun migrateLocation(request: UpsertLocationRequest): Location =
    webClient.post()
      .uri("/migrate/location")
      .bodyValue(request)
      .retrieve()
      .awaitBody()

  suspend fun migrateLocationHistory(id: UUID, request: MigrateHistoryRequest): ChangeHistory? =
    webClient.post()
      .uri("/migrate/location/{id}/history", id)
      .bodyValue(request)
      .retrieve()
      .awaitBodyOrNull()
}
