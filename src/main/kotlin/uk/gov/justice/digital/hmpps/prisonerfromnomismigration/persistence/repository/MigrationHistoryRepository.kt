package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository

import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Repository

@Repository
interface MigrationHistoryRepository :
  MigrationHistoryCustomRepository,
  CoroutineCrudRepository<MigrationHistory, String>
