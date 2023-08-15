package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Filter specifying what should be migrated from NOMIS to Activities service")
data class ActivitiesMigrationFilter(
  @Schema(
    description = "Only include appointments for this prison id",
    example = "MDI",
  )
  val prisonId: String,

  @Schema(
    description = "Only include a single course activity",
    example = "12345",
  )
  val courseActivityId: Long? = null,
)
