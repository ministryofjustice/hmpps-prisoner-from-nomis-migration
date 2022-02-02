package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visits

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Filter specifying what should be migrated from NOMIS to Visits service")
class VisitsMigrationFilter {
  @Schema(description = "List of prison Ids (AKA Agency Ids) to migrate visits from", example = "MDI", required = true)
  val prisonIds: List<String> = emptyList()
}
