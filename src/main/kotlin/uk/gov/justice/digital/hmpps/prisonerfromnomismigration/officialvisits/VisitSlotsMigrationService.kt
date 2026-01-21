package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.ByPageNumber
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.ByPageNumberMigrationService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationPage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType
import java.util.*

@Service
class VisitSlotsMigrationService(
  private val visitSlotsMappingService: VisitSlotsMappingService,
  private val nomisApiService: VisitSlotsNomisApiService,
  private val dpsApiService: OfficialVisitsDpsApiService,
  jsonMapper: JsonMapper,
  @Value("\${visitslots.page.size:1000}") pageSize: Long,
  @Value("\${visitslots.complete-check.delay-seconds}") completeCheckDelaySeconds: Int,
  @Value("\${visitslots.complete-check.retry-seconds:1}") completeCheckRetrySeconds: Int,
  @Value("\${visitslots.complete-check.count}") completeCheckCount: Int,
  @Value("\${complete-check.scheduled-retry-seconds}") completeCheckScheduledRetrySeconds: Int,
) : ByPageNumberMigrationService<Any, VisitTimeSlotIdResponse, VisitTimeSlotMigrationMappingDto>(
  mappingService = visitSlotsMappingService,
  migrationType = MigrationType.VISIT_SLOTS,
  pageSize = pageSize,
  completeCheckDelaySeconds = completeCheckDelaySeconds,
  completeCheckCount = completeCheckRetrySeconds,
  completeCheckRetrySeconds = completeCheckCount,
  completeCheckScheduledRetrySeconds = completeCheckScheduledRetrySeconds,
  jsonMapper = jsonMapper,
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
    val alreadyMigratedMapping = visitSlotsMappingService.getTimeSlotByNomisIdsOrNull(
      nomisPrisonId = nomisId.prisonId,
      nomisDayOfWeek = nomisId.dayOfWeek.name,
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
        nomisDayOfWeek = nomisId.dayOfWeek.name,
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
  override suspend fun retryCreateMapping(context: MigrationContext<VisitTimeSlotMigrationMappingDto>) = createMappingOrOnFailureDo(context, context.body) {
    throw it
  }

  private suspend fun VisitTimeSlotResponse.toMigrateVisitConfigRequest(): MigrateVisitConfigRequest = MigrateVisitConfigRequest(
    prisonCode = prisonId,
    dayCode = dayOfWeek.name,
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
        createDateTime = visitSlot.audit.createDatetime,
        createUsername = visitSlot.audit.createUsername,
        modifyDateTime = visitSlot.audit.modifyDatetime,
        modifyUsername = visitSlot.audit.modifyUserId,
      )
    },
    expiryDate = expiryDate,
    createDateTime = audit.createDatetime,
    createUsername = audit.createUsername,
    modifyDateTime = audit.modifyDatetime,
    modifyUsername = audit.modifyUserId,
  )

  private suspend fun VisitInternalLocationResponse.lookUpDpsLocationId(): UUID = visitSlotsMappingService.getInternalLocationByNomisId(id).dpsLocationId.let { UUID.fromString(it) }
  override fun parseContextFilter(json: String): MigrationMessage<*, Any> = jsonMapper.readValue(json)

  override fun parseContextPageFilter(json: String): MigrationMessage<*, MigrationPage<Any, ByPageNumber>> = jsonMapper.readValue(json)

  override fun parseContextNomisId(json: String): MigrationMessage<*, VisitTimeSlotIdResponse> = jsonMapper.readValue(json)

  override fun parseContextMapping(json: String): MigrationMessage<*, VisitTimeSlotMigrationMappingDto> = jsonMapper.readValue(json)
}

private fun VisitTimeSlotIdResponse.DayOfWeek.asNomisApiDayOfWeek(): VisitsConfigurationResourceApi.DayOfWeekGetVisitTimeSlot = when (this) {
  VisitTimeSlotIdResponse.DayOfWeek.MON -> VisitsConfigurationResourceApi.DayOfWeekGetVisitTimeSlot.MON
  VisitTimeSlotIdResponse.DayOfWeek.TUE -> VisitsConfigurationResourceApi.DayOfWeekGetVisitTimeSlot.TUE
  VisitTimeSlotIdResponse.DayOfWeek.WED -> VisitsConfigurationResourceApi.DayOfWeekGetVisitTimeSlot.WED
  VisitTimeSlotIdResponse.DayOfWeek.THU -> VisitsConfigurationResourceApi.DayOfWeekGetVisitTimeSlot.THU
  VisitTimeSlotIdResponse.DayOfWeek.FRI -> VisitsConfigurationResourceApi.DayOfWeekGetVisitTimeSlot.FRI
  VisitTimeSlotIdResponse.DayOfWeek.SAT -> VisitsConfigurationResourceApi.DayOfWeekGetVisitTimeSlot.SAT
  VisitTimeSlotIdResponse.DayOfWeek.SUN -> VisitsConfigurationResourceApi.DayOfWeekGetVisitTimeSlot.SUN
}
