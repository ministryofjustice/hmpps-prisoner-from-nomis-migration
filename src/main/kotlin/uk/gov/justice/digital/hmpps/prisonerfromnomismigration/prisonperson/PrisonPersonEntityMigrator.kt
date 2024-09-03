package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PrisonPersonMigrationMappingRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PrisonPersonMigrationMappingRequest.MigrationType.PHYSICAL_ATTRIBUTES
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.physicalattributes.PhysAttrEntityMigrator as PhysicalAttributesMigrator

interface PrisonPersonEntityMigrator {
  suspend fun migrate(offenderNo: String): DpsResponse
}

@Service
class PrisonPersonEntityMigratorService(
  private val entityMigrators: List<PrisonPersonEntityMigrator>,
) {
  fun migrator(migrationType: PrisonPersonMigrationMappingRequest.MigrationType): PrisonPersonEntityMigrator =
    when (migrationType) {
      PHYSICAL_ATTRIBUTES -> entityMigrators.find { it is PhysicalAttributesMigrator }
    }
      ?: error("No Entity Migrator found for migrationType=$migrationType")
}
