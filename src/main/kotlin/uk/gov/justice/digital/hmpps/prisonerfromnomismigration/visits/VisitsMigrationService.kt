package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visits

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.generateBatchId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.Messages.MIGRATE_VISIT
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.Messages.MIGRATE_VISITS
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.Messages.MIGRATE_VISITS_BY_PAGE
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.VisitId

@Service
class VisitsMigrationService(
  private val queueService: MigrationQueueService,
  private val nomisApiService: NomisApiService,
  private val visitMappingService: VisitMappingService,
  @Value("\${visits.page.size:1000}") private val pageSize: Long
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun migrateVisits(migrationFilter: VisitsMigrationFilter): MigrationContext<VisitsMigrationFilter> {
    val visitCount = nomisApiService.getVisits(
      prisonIds = migrationFilter.prisonIds,
      visitTypes = migrationFilter.visitTypes,
      fromDateTime = migrationFilter.fromDateTime,
      toDateTime = migrationFilter.toDateTime,
      pageNumber = 0,
      pageSize = 1,
    ).totalElements

    return MigrationContext(
      migrationId = generateBatchId(),
      body = migrationFilter,
      estimatedCount = visitCount
    ).apply {
      queueService.sendMessage(MIGRATE_VISITS, this)
    }
  }

  fun divideVisitsByPage(context: MigrationContext<VisitsMigrationFilter>) {
    (1..context.estimatedCount step pageSize).asSequence()
      .map {
        MigrationContext(
          context = context,
          body = VisitsPage(filter = context.body, pageNumber = it / pageSize, pageSize = pageSize)
        )
      }
      .forEach {
        queueService.sendMessage(MIGRATE_VISITS_BY_PAGE, it)
      }
  }

  fun migrateVisitsForPage(context: MigrationContext<VisitsPage>) = nomisApiService.getVisits(
    prisonIds = context.body.filter.prisonIds,
    visitTypes = context.body.filter.visitTypes,
    fromDateTime = context.body.filter.fromDateTime,
    toDateTime = context.body.filter.toDateTime,
    pageNumber = context.body.pageNumber,
    pageSize = context.body.pageSize
  ).content.map {
    MigrationContext(
      context = context,
      body = it
    )
  }.forEach { queueService.sendMessage(MIGRATE_VISIT, it) }

  fun migrateVisit(context: MigrationContext<VisitId>) =
    visitMappingService.findNomisVisitMapping(context.body.visitId)
      ?.run {
        log.error("Will not migrate visit since it is migrated already, NOMIS id is ${context.body.visitId}, VSIP id is ${this.vsipId} as part migration ${this.label ?: "NONE"} (${this.mappingType})")
      }
      ?: run {
        val nomisVisit = nomisApiService.getVisit(context.body.visitId)
        log.info("Migrating visit {}", nomisVisit)
        val room = nomisVisit.agencyInternalLocation?.let {
          visitMappingService.findRoomMapping(
            agencyInternalLocationCode = nomisVisit.agencyInternalLocation.code,
            prisonId = nomisVisit.prisonId
          )
        }
        log.info("Visit room {}", room ?: "NONE")
        // TODO - call VSIP to migrate visit
        // TODO - call mapping service to add mapping
      }
}

data class VisitsPage(val filter: VisitsMigrationFilter, val pageNumber: Long, val pageSize: Long)
