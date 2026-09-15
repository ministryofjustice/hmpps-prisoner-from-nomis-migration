package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.drugtesting

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Filter specifying what should be migrated from NOMIS to Drug Testing service")
data class DrugTestingMigrationFilter(
  @Schema(
    description = "Only include drug tests for these prison ids",
    example = "['MDI','LEI']",
  )
  val includedPrisonIds: Set<String> = emptySet(),

  @Schema(
    description = "Exclude drug tests for these prison ids",
    example = "['MDI','LEI']",
  )
  val excludedPrisonIds: Set<String> = emptySet(),
)
