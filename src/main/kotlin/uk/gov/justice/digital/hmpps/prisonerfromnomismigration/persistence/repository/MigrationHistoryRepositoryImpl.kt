package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository

import kotlinx.coroutines.flow.Flow
import org.springframework.data.domain.Sort
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate
import org.springframework.data.r2dbc.core.flow
import org.springframework.data.relational.core.query.Criteria.where
import org.springframework.data.relational.core.query.CriteriaDefinition
import org.springframework.data.relational.core.query.Query
import org.springframework.data.relational.core.query.Query.query
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.HistoryFilter

interface MigrationHistoryCustomRepository {
  fun findAllWithFilter(filter: HistoryFilter): Flow<MigrationHistory>
}

@Repository
class MigrationHistoryRepositoryImpl(private val template: R2dbcEntityTemplate) : MigrationHistoryCustomRepository {
  override fun findAllWithFilter(filter: HistoryFilter): Flow<MigrationHistory> {
    return template.select(MigrationHistory::class.java)
      .from("migration_history")
      .matching(
        buildQuery(filter)
          .sort(Sort.by("when_started").descending())
      )
      .flow()
  }

  private fun buildQuery(filter: HistoryFilter): Query =
    query(
      CriteriaDefinition.from(
        mutableListOf<CriteriaDefinition>().apply {
          this and filter.fromDateTime?.let {
            where("when_started").greaterThanOrEquals(it)
          }
          this and filter.toDateTime?.let {
            where("when_started").lessThanOrEquals(it)
          }
          this and filter.includeOnlyFailures.takeIf { it }?.let {
            where("records_failed").greaterThan(0)
          }
          this and filter.migrationTypes?.takeIf { it.isNotEmpty() }?.let {
            where("migration_type").`in`(it)
          }
          this and filter.filterContains?.let {
            where("filter").like("%$it%")
          }
        }
      )
    )
}

private infix fun MutableList<CriteriaDefinition>.and(criteria: CriteriaDefinition?) {
  criteria?.run { add(this) }
}
