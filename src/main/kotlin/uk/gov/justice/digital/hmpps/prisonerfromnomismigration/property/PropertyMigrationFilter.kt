package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.property

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Filter specifying what should be migrated from NOMIS to Property service")
data class PropertyMigrationFilter(
  @Schema(
    description = "Only include appointments for these prison ids",
    example = "['MDI','LEI']",
  )
  val prisonIds: List<String> = emptyList(),
)
