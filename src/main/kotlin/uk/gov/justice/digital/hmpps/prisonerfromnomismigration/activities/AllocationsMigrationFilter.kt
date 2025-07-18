package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Filter specifying which allocations should be migrated from NOMIS to DPS service")
data class AllocationsMigrationFilter(
  @Schema(
    description = "Only include allocations for this prison id",
    example = "MDI",
  )
  val prisonId: String,

  @Schema(
    description = "Only include allocations from a single course activity",
    example = "12345",
  )
  val courseActivityId: Long? = null,

  @Schema(
    description = "The date the new activity will start. This should be the same as the related activity migration.",
    example = "2025-01-31",
  )
  var activityStartDate: LocalDate? = LocalDate.now().plusDays(1),
)
