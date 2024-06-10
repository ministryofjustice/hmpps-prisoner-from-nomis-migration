package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrNullWhenNotFound
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.model.NomisSyncReportId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.model.NomisSyncRequest

@Service
class IncidentsService(@Qualifier("incidentsApiWebClient") private val webClient: WebClient) {
  suspend fun upsertIncident(migrateRequest: NomisSyncRequest): NomisSyncReportId =
    webClient.post()
      .uri("/sync/upsert")
      .bodyValue(migrateRequest)
      .retrieve()
      .awaitBody()

  suspend fun deleteIncident(incidentId: String) =
    webClient.delete()
      .uri("/incident-reports/{incidentId}", incidentId)
      .retrieve()
      .awaitBodyOrNullWhenNotFound<Unit>()
}
