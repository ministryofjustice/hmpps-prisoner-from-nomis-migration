package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.VisitSlotMigrationMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.VisitTimeSlotMigrationMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.api.VisitsConfigurationResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.VisitInternalLocationResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.VisitTimeSlotIdResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.VisitTimeSlotResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.MigrateVisitConfigRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.MigrateVisitSlot
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.util.*

@Service
class VisitSlotsMigrationService(
  private val visitSlotsMappingService: VisitSlotsMappingService,
  private val nomisApiService: VisitSlotsNomisApiService,
  private val dpsApiService: OfficialVisitsDpsApiService,
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
    } ?: run {
      val nomisVisitTimeSlot = nomisApiService.getVisitTimeSlot(
        prisonId = nomisId.prisonId,
        dayOfWeek = nomisId.dayOfWeek.asNomisApiDayOfWeek(),
        timeSlotSequence = nomisId.timeSlotSequence,
      )
      val dpsVisitTimeSlot = dpsApiService.migrateVisitConfiguration(nomisVisitTimeSlot.toMigrateVisitConfigRequest())

      val mapping = VisitTimeSlotMigrationMappingDto(
        dpsId = dpsVisitTimeSlot.dpsTimeSlotId.toString(),
        nomisPrisonId = nomisId.prisonId,
        nomisDayOfWeek = nomisId.dayOfWeek.asMappingMigrationDayOfWeek(),
        nomisSlotSequence = nomisId.timeSlotSequence,
        visitSlots = dpsVisitTimeSlot.visitSlots.map {
          VisitSlotMigrationMappingDto(
            dpsId = it.dpsId.toString(),
            nomisId = it.nomisId,
          )
        },
        mappingType = VisitTimeSlotMigrationMappingDto.MappingType.MIGRATED,
        label = context.migrationId,
      )
      createMappingOrOnFailureDo(context, mapping) {
        queueService.sendMessage(
          MigrationMessageType.RETRY_MIGRATION_MAPPING,
          MigrationContext(
            context = context,
            body = mapping,
          ),
        )
      }
    }
  }

  suspend fun createMappingOrOnFailureDo(
    context: MigrationContext<*>,
    mapping: VisitTimeSlotMigrationMappingDto,
    failureHandler: suspend (error: Throwable) -> Unit,
  ) {
    runCatching {
      mappingService.createMapping(mapping, object : ParameterizedTypeReference<DuplicateErrorResponse<VisitTimeSlotMigrationMappingDto>>() {})
    }.onFailure {
      failureHandler(it)
    }.onSuccess {
      if (it.isError) {
        val duplicateErrorDetails = it.errorResponse!!.moreInfo
        telemetryClient.trackEvent(
          "nomis-migration-visitslots-duplicate",
          mapOf(
            "duplicateDpsId" to duplicateErrorDetails.duplicate.dpsId,
            "duplicateNomisPrisonId" to duplicateErrorDetails.duplicate.nomisPrisonId,
            "duplicateNomisDayOfWeek" to duplicateErrorDetails.duplicate.nomisDayOfWeek,
            "duplicateNomisSlotSequence" to duplicateErrorDetails.duplicate.nomisSlotSequence,
            "existingDpsId" to duplicateErrorDetails.existing.dpsId,
            "existingNomisPrisonId" to duplicateErrorDetails.existing.nomisPrisonId,
            "existingNomisDayOfWeek" to duplicateErrorDetails.existing.nomisDayOfWeek,
            "existingNomisSlotSequence" to duplicateErrorDetails.existing.nomisSlotSequence,
            "migrationId" to context.migrationId,
          ),
        )
      } else {
        telemetryClient.trackEvent(
          "visitslots-migration-entity-migrated",
          mapOf(
            "nomisPrisonId" to mapping.nomisPrisonId,
            "nomisDayOfWeek" to mapping.nomisDayOfWeek,
            "nomisSlotSequence" to mapping.nomisSlotSequence,
            "dpsId" to mapping.dpsId,
            "migrationId" to context.migrationId,
          ),
        )
      }
    }
  }

  private suspend fun VisitTimeSlotResponse.toMigrateVisitConfigRequest(): MigrateVisitConfigRequest = MigrateVisitConfigRequest(
    prisonCode = prisonId,
    dayCode = dayOfWeek.asDpsApiDayOfWeek(),
    timeSlotSeq = timeSlotSequence,
    startTime = startTime,
    endTime = endTime,
    effectiveDate = effectiveDate,
    visitSlots = visitSlots.map { visitSlot ->
      MigrateVisitSlot(
        agencyVisitSlotId = visitSlot.id,
        dpsLocationId = visitSlot.internalLocation.lookUpDpsLocationId(),
        maxGroups = visitSlot.maxGroups ?: 0,
        maxAdults = visitSlot.maxAdults ?: 0,
        internalLocationId = visitSlot.internalLocation.id,
        locationKey = visitSlot.internalLocation.code,
        maxVideoSessions = null,
        createDateTime = LocalDateTime.now(),
        createUsername = "TODO()",
        modifyDateTime = null,
        modifyUsername = null,
      )
    },
    expiryDate = expiryDate,
    createDateTime = LocalDateTime.now(),
    createUsername = "TODO()",
    modifyDateTime = null,
    modifyUsername = null,
  )

  private suspend fun VisitInternalLocationResponse.lookUpDpsLocationId(): UUID = visitSlotsMappingService.getInternalLocationByNomisId(id).dpsLocationId.let { UUID.fromString(it) }
}

private fun VisitTimeSlotIdResponse.DayOfWeek.asNomisApiDayOfWeek(): VisitsConfigurationResourceApi.DayOfWeekGetVisitTimeSlot = when (this) {
  VisitTimeSlotIdResponse.DayOfWeek.MONDAY -> VisitsConfigurationResourceApi.DayOfWeekGetVisitTimeSlot.MONDAY
  VisitTimeSlotIdResponse.DayOfWeek.TUESDAY -> VisitsConfigurationResourceApi.DayOfWeekGetVisitTimeSlot.TUESDAY
  VisitTimeSlotIdResponse.DayOfWeek.WEDNESDAY -> VisitsConfigurationResourceApi.DayOfWeekGetVisitTimeSlot.WEDNESDAY
  VisitTimeSlotIdResponse.DayOfWeek.THURSDAY -> VisitsConfigurationResourceApi.DayOfWeekGetVisitTimeSlot.THURSDAY
  VisitTimeSlotIdResponse.DayOfWeek.FRIDAY -> VisitsConfigurationResourceApi.DayOfWeekGetVisitTimeSlot.FRIDAY
  VisitTimeSlotIdResponse.DayOfWeek.SATURDAY -> VisitsConfigurationResourceApi.DayOfWeekGetVisitTimeSlot.SATURDAY
  VisitTimeSlotIdResponse.DayOfWeek.SUNDAY -> VisitsConfigurationResourceApi.DayOfWeekGetVisitTimeSlot.SUNDAY
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
private fun VisitTimeSlotIdResponse.DayOfWeek.asMappingMigrationDayOfWeek(): VisitTimeSlotMigrationMappingDto.NomisDayOfWeek = when (this) {
  VisitTimeSlotIdResponse.DayOfWeek.MONDAY -> VisitTimeSlotMigrationMappingDto.NomisDayOfWeek.MONDAY
  VisitTimeSlotIdResponse.DayOfWeek.TUESDAY -> VisitTimeSlotMigrationMappingDto.NomisDayOfWeek.TUESDAY
  VisitTimeSlotIdResponse.DayOfWeek.WEDNESDAY -> VisitTimeSlotMigrationMappingDto.NomisDayOfWeek.WEDNESDAY
  VisitTimeSlotIdResponse.DayOfWeek.THURSDAY -> VisitTimeSlotMigrationMappingDto.NomisDayOfWeek.THURSDAY
  VisitTimeSlotIdResponse.DayOfWeek.FRIDAY -> VisitTimeSlotMigrationMappingDto.NomisDayOfWeek.FRIDAY
  VisitTimeSlotIdResponse.DayOfWeek.SATURDAY -> VisitTimeSlotMigrationMappingDto.NomisDayOfWeek.SATURDAY
  VisitTimeSlotIdResponse.DayOfWeek.SUNDAY -> VisitTimeSlotMigrationMappingDto.NomisDayOfWeek.SUNDAY
}

private fun VisitTimeSlotResponse.DayOfWeek.asDpsApiDayOfWeek(): String = when (this) {
  VisitTimeSlotResponse.DayOfWeek.MONDAY,
  VisitTimeSlotResponse.DayOfWeek.TUESDAY,
  VisitTimeSlotResponse.DayOfWeek.WEDNESDAY,
  VisitTimeSlotResponse.DayOfWeek.THURSDAY,
  VisitTimeSlotResponse.DayOfWeek.FRIDAY,
  VisitTimeSlotResponse.DayOfWeek.SATURDAY,
  VisitTimeSlotResponse.DayOfWeek.SUNDAY,
  -> this.name.take(3)
}
