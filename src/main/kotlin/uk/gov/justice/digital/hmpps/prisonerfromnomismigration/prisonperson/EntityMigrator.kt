package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PrisonPersonMigrationMappingRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PrisonPersonMigrationMappingRequest.MigrationType.PHYSICAL_ATTRIBUTES
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.physicalattributes.EntityMigrator as PhysicalAttributesMigrator

interface EntityMigrator {
  suspend fun migrate(offenderNo: String): DpsResponse
}

@Service
class EntityMigratorService(
  private val entityMigrators: List<EntityMigrator>,
) {
  fun migrator(migrationType: PrisonPersonMigrationMappingRequest.MigrationType): EntityMigrator =
    when (migrationType) {
      PHYSICAL_ATTRIBUTES -> entityMigrators.find { it is PhysicalAttributesMigrator }
    }
      ?: error("No Entity Migrator found for migrationType=$migrationType")
}
