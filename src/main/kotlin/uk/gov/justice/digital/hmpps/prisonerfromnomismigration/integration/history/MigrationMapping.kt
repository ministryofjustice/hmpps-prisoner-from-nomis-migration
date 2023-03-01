package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history

import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.LatestMigration
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationDetails

interface MigrationMapping {
  suspend fun findLatestMigration(): LatestMigration?

  suspend fun getMigrationDetails(migrationId: String): MigrationDetails

  suspend fun getMigrationCount(migrationId: String): Long
}
