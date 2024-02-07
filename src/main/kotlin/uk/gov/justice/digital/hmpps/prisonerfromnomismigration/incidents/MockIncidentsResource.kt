package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.Valid
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

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
  suspend fun createAdjudicationsForMigration(
    @RequestBody @Valid
    incidentRequest: IncidentMigrateRequest,
  ): IncidentMigrateResponse {
    log.info("Created incident for migration with id ${incidentRequest.incidentId} ")
    return IncidentMigrateResponse(incidentRequest.incidentId)
  }
}

data class IncidentMigrateRequest(
  /* NOMIS Incident ID */
  val incidentId: Long,
  /* Basic Description for the incident */
  val description: String?,
)

data class IncidentMigrateResponse(
  /* DPS Incident ID */
  @field:JsonProperty("incidentId")
  val incidentId: kotlin.Long,

)
