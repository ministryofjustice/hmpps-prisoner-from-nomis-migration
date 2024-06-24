package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.IncidentAgencyId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.doApiCallWithRetries

@Service
class IncidentsReconciliationService(
  private val telemetryClient: TelemetryClient,
  private val incidentsService: IncidentsService,
  private val nomisIncidentsApiService: IncidentsNomisApiService,
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun generateReconciliationReport(agencies: List<IncidentAgencyId>): List<MismatchIncidents> =
    withContext(Dispatchers.Unconfined) {
      agencies.map { async { checkIncidentsMatch(it.agencyId) } }
    }.awaitAll().filterNotNull()

  suspend fun checkIncidentsMatch(agencyId: String): MismatchIncidents? = runCatching {
    val nomisIncidents = doApiCallWithRetries { nomisIncidentsApiService.getIncidentsReconciliation(agencyId) }
    val dpsOpenIncidentsCount = doApiCallWithRetries { incidentsService.getOpenIncidentsCount(agencyId) }
    val dpsClosedIncidentsCount = doApiCallWithRetries { incidentsService.getClosedIncidentsCount(agencyId) }
    val nomisOpenIncidentsCount = nomisIncidents.incidentCount.openIncidents
    val nomisClosedIncidentsCount = nomisIncidents.incidentCount.closedIncidents

    return if (nomisOpenIncidentsCount != dpsOpenIncidentsCount || nomisClosedIncidentsCount != dpsClosedIncidentsCount) {
      MismatchIncidents(
        agencyId = agencyId,
        dpsOpenIncidents = dpsOpenIncidentsCount,
        nomisOpenIncidents = nomisOpenIncidentsCount,
        dpsClosedIncidents = dpsOpenIncidentsCount,
        nomisClosedIncidents = nomisClosedIncidentsCount,
      )
        .also { mismatch ->
          log.info("Incidents Mismatch found  $mismatch")
          telemetryClient.trackEvent(
            "incidents-reports-reconciliation-mismatch",
            mapOf(
              "agencyId" to mismatch.agencyId,
              "dpsOpenIncidents" to mismatch.dpsOpenIncidents,
              "nomisOpenIncidents" to mismatch.nomisOpenIncidents,
              "dpsClosedIncidents" to mismatch.dpsClosedIncidents,
              "nomisClosedIncidents" to mismatch.nomisClosedIncidents,
            ),
          )
        }
    } else {
      null
    }
  }.onFailure {
    log.error("Unable to match incidents for agency with $agencyId ", it)
    telemetryClient.trackEvent(
      "incidents-reports-reconciliation-mismatch-error",
      mapOf(
        "agencyId" to agencyId,
      ),
    )
  }.getOrNull()
}

data class MismatchIncidents(
  val agencyId: String,
  val dpsOpenIncidents: Long,
  val nomisOpenIncidents: Long,
  val dpsClosedIncidents: Long,
  val nomisClosedIncidents: Long,
)
