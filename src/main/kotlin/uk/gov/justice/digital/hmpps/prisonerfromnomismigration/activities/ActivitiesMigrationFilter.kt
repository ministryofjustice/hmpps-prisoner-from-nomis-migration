package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Filter specifying which activities should be migrated from NOMIS to DPS service")
data class ActivitiesMigrationFilter(
  @Schema(
    description = "Only include activities for this prison id",
    example = "MDI",
  )
  val prisonId: String,

  @Schema(
    description = "Only include a single course activity",
    example = "12345",
  )
  val courseActivityId: Long? = null,
)
