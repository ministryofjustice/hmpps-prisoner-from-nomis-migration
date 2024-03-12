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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.IncidentResponse

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
    nomisIncident: IncidentResponse,
  ): Incident {
    log.info("Created incident for migration with id ${nomisIncident.incidentId} ")
    return Incident("DPS-${nomisIncident.incidentId}")
  }

  @PreAuthorize("hasRole('ROLE_MIGRATE_INCIDENTS')")
  @PutMapping("/incidents/sync")
  @Operation(hidden = true)
  suspend fun syncIncidentsForMigration(
    @RequestBody @Valid
    nomisIncident: IncidentResponse,
  ): Incident {
    log.info("Synced incident for migration with id ${nomisIncident.incidentId} ")
    return Incident("DPS-${nomisIncident.incidentId}")
  }
}

data class Incident(
  /* DPS Incident ID */
  val id: String,
)
