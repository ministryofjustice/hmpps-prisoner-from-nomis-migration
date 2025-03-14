package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visitbalances

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Filter specifying what should be migrated from NOMIS to DPS")
data class VisitBalanceMigrationFilter(
  @Schema(
    description = "Only include visit balances for this prison id",
    example = "MDI",
  )
  val prisonId: String? = null,
)
