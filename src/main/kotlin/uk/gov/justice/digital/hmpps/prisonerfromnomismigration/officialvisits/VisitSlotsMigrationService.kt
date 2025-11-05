package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.VisitTimeSlotMigrationMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.VisitTimeSlotIdResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType
import java.time.DayOfWeek

@Service
class VisitSlotsMigrationService(
  private val visitSlotsMappingService: VisitSlotsMappingService,
  private val nomisApiService: VisitSlotsNomisApiService,
  @Value("\${visitslots.page.size:1000}") pageSize: Long,
  @Value("\${visitslots.complete-check.delay-seconds}") completeCheckDelaySeconds: Int,
  @Value("\${visitslots.complete-check.retry-seconds:1}") completeCheckRetrySeconds: Int,
  @Value("\${visitslots.complete-check.count}") completeCheckCount: Int,
  @Value("\${complete-check.scheduled-retry-seconds}") completeCheckScheduledRetrySeconds: Int,
) : MigrationService<Any, VisitTimeSlotIdResponse, VisitTimeSlotMigrationMappingDto>(
  mappingService = visitSlotsMappingService,
  migrationType = MigrationType.VISIT_SLOTS,
  pageSize = pageSize,
  completeCheckDelaySeconds = completeCheckDelaySeconds,
  completeCheckCount = completeCheckRetrySeconds,
  completeCheckRetrySeconds = completeCheckCount,
  completeCheckScheduledRetrySeconds = completeCheckScheduledRetrySeconds,
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  override suspend fun getPageOfIds(
    migrationFilter: Any,
    pageSize: Long,
    pageNumber: Long,
  ): List<VisitTimeSlotIdResponse> = nomisApiService.getVisitTimeSlotIds(
    pageNumber = pageNumber,
    pageSize = pageSize,
  ).content!!

  override suspend fun getTotalNumberOfIds(migrationFilter: Any): Long = nomisApiService.getVisitTimeSlotIds(
    pageNumber = 0,
    pageSize = 1,
  ).page!!.totalElements!!

  override suspend fun migrateNomisEntity(context: MigrationContext<VisitTimeSlotIdResponse>) {
    val nomisId = context.body
    val alreadyMigratedMapping = visitSlotsMappingService.getByNomisIdsOrNull(
      nomisPrisonId = nomisId.prisonId,
      nomisDayOfWeek = nomisId.dayOfWeek.asMappingDayOfWeek(),
      nomisSlotSequence = nomisId.timeSlotSequence,
    )

    alreadyMigratedMapping?.run {
      log.info("Will not migrate the nomis visit time slot=$nomisPrisonId;$nomisDayOfWeek;$nomisSlotSequence since it was already mapped to DPS visits slot time $dpsId during migration $label")
    }
  }
}

private fun VisitTimeSlotIdResponse.DayOfWeek.asMappingDayOfWeek(): DayOfWeek = when (this) {
  VisitTimeSlotIdResponse.DayOfWeek.MONDAY -> DayOfWeek.MONDAY
  VisitTimeSlotIdResponse.DayOfWeek.TUESDAY -> DayOfWeek.TUESDAY
  VisitTimeSlotIdResponse.DayOfWeek.WEDNESDAY -> DayOfWeek.WEDNESDAY
  VisitTimeSlotIdResponse.DayOfWeek.THURSDAY -> DayOfWeek.THURSDAY
  VisitTimeSlotIdResponse.DayOfWeek.FRIDAY -> DayOfWeek.FRIDAY
  VisitTimeSlotIdResponse.DayOfWeek.SATURDAY -> DayOfWeek.SATURDAY
  VisitTimeSlotIdResponse.DayOfWeek.SUNDAY -> DayOfWeek.SUNDAY
}
