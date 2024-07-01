package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrNullWhenNotFound
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.model.NomisSyncReportId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.model.NomisSyncRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.model.ReportBasic
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.model.ReportWithDetails
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.model.SimplePageReportBasic

@Service
class IncidentsService(@Qualifier("incidentsApiWebClient") private val webClient: WebClient) {
  companion object {
    val openStatusValues = listOf("AWAITING_ANALYSIS", "IN_ANALYSIS", "INFORMATION_REQUIRED", "INFORMATION_AMENDED", "POST_INCIDENT_UPDATE", "INCIDENT_UPDATED")
    val closedStatusValues = listOf("CLOSED", "DUPLICATE")
  }

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

  suspend fun getIncidentByNomisId(nomisIncidentId: Long): ReportBasic =
    webClient.get()
      .uri("/incident-reports/incident-number/{nomisIncidentId}", nomisIncidentId)
      .retrieve()
      .awaitBody()

  suspend fun getIncidentDetailsByNomisId(nomisIncidentId: Long): ReportWithDetails =
    webClient.get()
      .uri("/incident-reports/incident-number/{nomisIncidentId}/with-details", nomisIncidentId)
      .retrieve()
      .awaitBody()

  suspend fun getIncidents(agencyId: String, statusValues: List<String>): SimplePageReportBasic =
    webClient.get()
      .uri {
        it.path("/incident-reports")
          .queryParam("prisonId", agencyId)
          .queryParam("status", statusValues)
          .queryParam("size", 1)
          .build()
      }
      .retrieve()
      .awaitBody()

  suspend fun getOpenIncidentsCount(agencyId: String) = getIncidents(agencyId, openStatusValues).totalElements
  suspend fun getClosedIncidentsCount(agencyId: String) = getIncidents(agencyId, closedStatusValues).totalElements
}
