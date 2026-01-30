package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csra

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Filter specifying who should be migrated from NOMIS to DPS")
data class PrisonerMigrationFilter(
  @Schema(
    description = "Only include prisoners for this prison id",
    example = "MDI",
  )
  val prisonId: String? = null,
)
