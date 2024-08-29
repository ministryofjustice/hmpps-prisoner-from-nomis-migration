package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson

interface PrisonPersonEntityMigrator<NOMIS_RESPONSE, DPS_REQUEST> {
  suspend fun migrateEntity(offenderNo: String): List<Long> =
    getNomisEntity(offenderNo)
      .toDpsMigrationRequests()
      .migrate(offenderNo)

  suspend fun getNomisEntity(offenderNo: String): NOMIS_RESPONSE
  suspend fun NOMIS_RESPONSE.toDpsMigrationRequests(): List<DPS_REQUEST>
  suspend fun List<DPS_REQUEST>.migrate(offenderNo: String): List<Long>
}
