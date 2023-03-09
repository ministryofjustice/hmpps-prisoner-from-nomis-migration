package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history

import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.LatestMigration
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationDetails

interface MigrationMapping<MAPPING : Any> {
  suspend fun findLatestMigration(): LatestMigration?

  suspend fun getMigrationDetails(migrationId: String): MigrationDetails

  suspend fun getMigrationCount(migrationId: String): Long

  suspend fun createMapping(mapping: MAPPING): CreateMappingResult
}

data class CreateMappingResult(
  /* currently, only interested in the error response as success doesn't return a body*/
  val errorResponse: Any? = null,
) {
  val isError
    get() = errorResponse != null
}
