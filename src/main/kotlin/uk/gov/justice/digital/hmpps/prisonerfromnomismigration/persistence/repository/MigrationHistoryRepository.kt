package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository

import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType

@Repository
interface MigrationHistoryRepository :
  MigrationHistoryCustomRepository,
  CoroutineCrudRepository<MigrationHistory, String> {
  suspend fun findFirstByMigrationTypeOrderByWhenStartedDesc(migrationType: MigrationType): MigrationHistory?
}
