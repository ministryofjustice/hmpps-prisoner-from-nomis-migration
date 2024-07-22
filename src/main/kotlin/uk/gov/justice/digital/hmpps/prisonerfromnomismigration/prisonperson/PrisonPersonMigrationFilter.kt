package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Filter specifying what should be migrated from NOMIS to DPS")
class PrisonPersonMigrationFilter(
  @Schema(
    description = "Only migrate a single prisoner - used for testing",
    example = "A1234BC",
  )
  val prisonerNumber: String?,
)
