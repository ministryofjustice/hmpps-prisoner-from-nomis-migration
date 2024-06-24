package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent

@RestController
@RequestMapping("/incidents/reports/reconciliation", produces = [MediaType.APPLICATION_JSON_VALUE])
class IncidentsReconciliationResource(
  private val incidentsReconciliationService: IncidentsReconciliationService,
  private val telemetryClient: TelemetryClient,
  private val reportScope: CoroutineScope,

  private val nomisIncidentsApiService: IncidentsNomisApiService,

) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @PutMapping
  @ResponseStatus(HttpStatus.ACCEPTED)
  suspend fun incidentsReconciliation() {
    nomisIncidentsApiService.getAllAgencies()
      .also { agencyIds ->
        telemetryClient.trackEvent(
          "incidents-reports-reconciliation-requested",
          mapOf("prisonCount" to agencyIds.size),
        )

        reportScope.launch {
          runCatching { incidentsReconciliationService.generateReconciliationReport(agencyIds) }
            .onSuccess {
              log.info("Incidents reconciliation report completed with ${it.size} mismatches")
              telemetryClient.trackEvent(
                "incidents-reports-reconciliation-report",
                mapOf("mismatch-count" to it.size.toString(), "success" to "true") + it.asMap(),
              )
            }
            .onFailure {
              telemetryClient.trackEvent("incidents-reports-reconciliation-report", mapOf("success" to "false"))
              log.error("Incidents reconciliation report failed", it)
            }
        }
      }
  }
}
private fun List<MismatchIncidents>.asMap(): Map<String, String> {
  return this.associate {
    it.agencyId to
      ("open-dps=${it.dpsOpenIncidents}:open-nomis=${it.nomisOpenIncidents}; closed-dps=${it.dpsClosedIncidents}:closed-nomis=${it.nomisClosedIncidents}")
  }
}
