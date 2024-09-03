package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PrisonPersonMigrationMappingRequest

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Filter specifying what should be migrated from NOMIS to DPS")
class PrisonPersonMigrationFilter(
  @Schema(
    description = "Only migrate a single prisoner - used for testing",
    example = "A1234BC",
  )
  val prisonerNumber: String?,

  @Schema(
    description = "The type of migration for this person, e.g. which data is being migrated",
    example = "PHYSICAL_ATTRIBUTES",
  )
  val migrationType: PrisonPersonMigrationMappingRequest.MigrationType = PrisonPersonMigrationMappingRequest.MigrationType.PHYSICAL_ATTRIBUTES,
)
