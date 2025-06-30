package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrLogAndRethrowBadRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrNullWhenNotFound
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.model.NomisSyncReportId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.model.NomisSyncRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.model.ReportBasic

@Service
class IncidentsService(@Qualifier("incidentsApiWebClient") private val webClient: WebClient) {

  suspend fun upsertIncident(syncRequest: NomisSyncRequest): NomisSyncReportId = webClient.post()
    .uri("/sync/upsert")
    .bodyValue(syncRequest)
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun deleteIncident(incidentId: String) = webClient.delete()
    .uri("/incident-reports/{incidentId}", incidentId)
    .retrieve()
    .awaitBodyOrNullWhenNotFound<Unit>()

  suspend fun getIncidentByNomisId(nomisIncidentId: Long): ReportBasic = webClient.get()
    .uri("/incident-reports/reference/{nomisIncidentId}", nomisIncidentId)
    .retrieve()
    .awaitBody()
}
