package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository

import kotlinx.coroutines.flow.Flow
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
interface MigrationHistoryRepository : CoroutineCrudRepository<MigrationHistory, String> {
  @Query(
    """
    select * from migration_history where 
    (:fromDateTime is null or when_started >= :fromDateTime) and
    (:toDateTime is null or when_started <= :toDateTime) and
    (:includeOnlyFailures = false or records_failed > 0) and
    (migration_type in (:migrationTypes)) and
    (:filterContains is null or filter like concat('%', :filterContains, '%') ) 
    """
  )
  fun findWithCriteria(
    @Param("fromDateTime") fromDateTime: LocalDateTime? = null,
    @Param("toDateTime") toDateTime: LocalDateTime? = null,
    @Param("includeOnlyFailures") includeOnlyFailures: Boolean = false,
    @Param("filterContains") filterContains: String? = null,
    @Param("migrationTypes") migrationTypes: List<String>? = null,
  ): Flow<MigrationHistory>
}
