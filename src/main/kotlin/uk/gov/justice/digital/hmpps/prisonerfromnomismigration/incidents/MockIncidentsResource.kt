package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents

import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.Valid
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime

/**
 * This represents the possible interface for the incidents api service.
 * This can be deleted once the real service is available.
 */
@RestController
class MockIncidentsResource {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @PreAuthorize("hasRole('ROLE_MIGRATE_INCIDENTS')")
  @PostMapping("/incidents/migrate")
  @Operation(hidden = true)
  suspend fun createIncidentsForMigration(
    @RequestBody @Valid
    incidentRequest: IncidentMigrateRequest,
  ): Incident {
    log.info("Created incident for migration with id ${incidentRequest.incidentReportNumber} ")
    return Incident("DPS-${incidentRequest.incidentReportNumber}")
  }

  @PreAuthorize("hasRole('ROLE_MIGRATE_INCIDENTS')")
  @PutMapping("/incidents/sync")
  @Operation(hidden = true)
  suspend fun syncIncidentsForMigration(
    @RequestBody @Valid
    incidentRequest: IncidentSyncRequest,
  ): Incident {
    log.info("Synced incident for migration with id ${incidentRequest.nomisIncidentId} ")
    return Incident("DPS-${incidentRequest.nomisIncidentId}")
  }
}

// TODO Add more fields to the incident api migrate request once we know requirements/structure
data class IncidentMigrateRequest(
  /* NOMIS Incident ID */
  val incidentReportNumber: Long,
  val reportDetails: IncidentReportDetails,
)

// TODO Add more fields to the incident api sync request once we know requirements
// Will we pass everything again - assume we will (for now) - it will just overwrite the old DPS incident
data class IncidentSyncRequest(
  /* NOMIS Incident ID */
  val nomisIncidentId: Long,
  /* Basic Description for the incident */
  val description: String?,
)

data class Incident(
  /* DPS Incident ID */
  val id: String,
)

data class IncidentReportDetails(
  val title: String?,
  val status: String,
  val reportType: String,
  val comments: String?,
  val prisonId: String,
  val reportDate: LocalDateTime,
  val incidentDate: LocalDateTime,
  val reportedBy: String,

)