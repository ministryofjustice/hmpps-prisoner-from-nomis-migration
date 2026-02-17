package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.EventAudited
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.EventAudited.Companion.DPS_SYNC_AUDIT_MODULE
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.TelemetryEnabled
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.track
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.valuesAsStrings
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.VisitSlotMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.VisitTimeSlotMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.api.VisitsConfigurationResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.VisitTimeSlotResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.DayType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.SyncCreateTimeSlotRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.SyncCreateVisitSlotRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.SyncUpdateTimeSlotRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.SyncUpdateVisitSlotRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.InternalMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationType
import java.util.*

@Service
class VisitSlotsSynchronisationService(
  override val telemetryClient: TelemetryClient,
  private val mappingApiService: VisitSlotsMappingService,
  private val nomisApiService: VisitSlotsNomisApiService,
  private val dpsApiService: OfficialVisitsDpsApiService,
  private val queueService: SynchronisationQueueService,
) : TelemetryEnabled {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun visitTimeslotAdded(event: AgencyVisitTimeEvent) {
    val telemetryName = "officialvisits-timeslot-synchronisation-created"
    if (event.isFromDPSOfficialVisits()) {
      telemetryClient.trackEvent("$telemetryName-skipped", event.asTelemetry())
    } else {
      val mapping = mappingApiService.getTimeSlotByNomisIdsOrNull(
        nomisPrisonId = event.agencyLocationId,
        nomisDayOfWeek = event.weekDay,
        nomisSlotSequence = event.timeslotSequence,
      )
      if (mapping != null) {
        telemetryClient.trackEvent(
          "$telemetryName-ignored",
          event.asTelemetry() + ("dpsPrisonTimeSlotId" to mapping.dpsId),
        )
      } else {
        val telemetry = event.asTelemetry()
        track(telemetryName, telemetry) {
          nomisApiService.getVisitTimeSlot(
            prisonId = event.agencyLocationId,
            dayOfWeek = event.weekDay.asNomisApiDayOfWeek(),
            timeSlotSequence = event.timeslotSequence,
          ).also { nomisTimeSlot ->
            dpsApiService.createTimeSlot(nomisTimeSlot.toSyncCreateTimeSlotRequest()).also { dpsTimeSlot ->
              telemetry["dpsPrisonTimeSlotId"] = dpsTimeSlot.prisonTimeSlotId
              tryToCreateMapping(
                VisitTimeSlotMappingDto(
                  dpsId = dpsTimeSlot.prisonTimeSlotId.toString(),
                  nomisPrisonId = nomisTimeSlot.prisonId,
                  nomisDayOfWeek = nomisTimeSlot.dayOfWeek.toString(),
                  nomisSlotSequence = nomisTimeSlot.timeSlotSequence,
                  mappingType = VisitTimeSlotMappingDto.MappingType.NOMIS_CREATED,
                ),
                telemetry = telemetry,
              )
            }
          }
        }
      }
    }
  }
  suspend fun visitTimeslotUpdated(event: AgencyVisitTimeEvent) {
    val telemetryName = "officialvisits-timeslot-synchronisation-updated"
    if (event.isFromDPSOfficialVisits()) {
      telemetryClient.trackEvent("$telemetryName-skipped", event.asTelemetry())
    } else {
      val telemetry = event.asTelemetry()
      track(telemetryName, telemetry) {
        val mapping = mappingApiService.getTimeSlotByNomisIds(
          nomisPrisonId = event.agencyLocationId,
          nomisDayOfWeek = event.weekDay,
          nomisSlotSequence = event.timeslotSequence,
        ).also {
          telemetry["dpsPrisonTimeSlotId"] = it.dpsId
        }

        nomisApiService.getVisitTimeSlot(
          prisonId = event.agencyLocationId,
          dayOfWeek = event.weekDay.asNomisApiDayOfWeek(),
          timeSlotSequence = event.timeslotSequence,
        ).also { nomisTimeSlot ->
          dpsApiService.updateTimeSlot(prisonTimeSlotId = mapping.dpsId.toLong(), nomisTimeSlot.toSyncUpdateTimeSlotRequest())
        }
      }
    }
  }

  suspend fun visitTimeslotDeleted(event: AgencyVisitTimeEvent) {
    val telemetryName = "officialvisits-timeslot-synchronisation-deleted"
    val telemetry = event.asTelemetry()
    mappingApiService.getTimeSlotByNomisIdsOrNull(
      nomisPrisonId = event.agencyLocationId,
      nomisDayOfWeek = event.weekDay,
      nomisSlotSequence = event.timeslotSequence,
    )?.also { mapping ->
      telemetry["dpsPrisonTimeSlotId"] = mapping.dpsId
      track(telemetryName, telemetry) {
        dpsApiService.deleteTimeSlot(mapping.dpsId.toLong())
        mappingApiService.deleteTimeSlotByNomisIds(
          nomisPrisonId = event.agencyLocationId,
          nomisDayOfWeek = event.weekDay,
          nomisSlotSequence = event.timeslotSequence,
        )
      }
    } ?: run {
      telemetryClient.trackEvent(
        "$telemetryName-ignored",
        telemetry,
      )
    }
  }
  suspend fun visitSlotAdded(event: AgencyVisitSlotEvent) {
    val telemetryName = "officialvisits-visitslot-synchronisation-created"
    if (event.isFromDPSOfficialVisits()) {
      telemetryClient.trackEvent("$telemetryName-skipped", event.asTelemetry())
    } else {
      val mapping = mappingApiService.getVisitSlotByNomisIdOrNull(event.agencyVisitSlotId)
      if (mapping != null) {
        telemetryClient.trackEvent(
          "$telemetryName-ignored",
          event.asTelemetry() + ("dpsPrisonVisitSlotId" to mapping.dpsId),
        )
      } else {
        val telemetry = event.asTelemetry()
        track(telemetryName, telemetry) {
          nomisApiService.getVisitTimeSlot(
            prisonId = event.agencyLocationId,
            dayOfWeek = event.weekDay.asNomisApiDayOfWeek(),
            timeSlotSequence = event.timeslotSequence,
          ).visitSlots.find { it.id == event.agencyVisitSlotId }!!.also { nomisVisitSlot ->
            val locationMapping = mappingApiService.getInternalLocationByNomisId(
              nomisLocationId = nomisVisitSlot.internalLocation.id,
            ).also { telemetry["dpsLocationId"] = it.dpsLocationId }
            val timeSlotMapping = mappingApiService.getTimeSlotByNomisIds(
              nomisPrisonId = event.agencyLocationId,
              nomisDayOfWeek = event.weekDay,
              nomisSlotSequence = event.timeslotSequence,
            ).also { telemetry["dpsPrisonTimeSlotId"] = it.dpsId }

            dpsApiService.createVisitSlot(
              SyncCreateVisitSlotRequest(
                prisonTimeSlotId = timeSlotMapping.dpsId.toLong(),
                dpsLocationId = UUID.fromString(locationMapping.dpsLocationId),
                createdBy = nomisVisitSlot.audit.createUsername,
                createdTime = nomisVisitSlot.audit.createDatetime,
                maxAdults = nomisVisitSlot.maxAdults,
                maxGroups = nomisVisitSlot.maxGroups,
              ),
            ).also { dpsVisitSlot ->
              telemetry["dpsVisitSlotId"] = dpsVisitSlot.visitSlotId
              tryToCreateMapping(
                VisitSlotMappingDto(
                  dpsId = dpsVisitSlot.visitSlotId.toString(),
                  nomisId = nomisVisitSlot.id,
                  mappingType = VisitSlotMappingDto.MappingType.NOMIS_CREATED,
                ),
                telemetry = telemetry,
              )
            }
          }
        }
      }
    }
  }
  suspend fun visitSlotUpdated(event: AgencyVisitSlotEvent) {
    val telemetryName = "officialvisits-visitslot-synchronisation-updated"
    if (event.isFromDPSOfficialVisits()) {
      telemetryClient.trackEvent("$telemetryName-skipped", event.asTelemetry())
    } else {
      val telemetry = event.asTelemetry()
      track(telemetryName, telemetry) {
        nomisApiService.getVisitTimeSlot(
          prisonId = event.agencyLocationId,
          dayOfWeek = event.weekDay.asNomisApiDayOfWeek(),
          timeSlotSequence = event.timeslotSequence,
        ).visitSlots.find { it.id == event.agencyVisitSlotId }!!.also { nomisVisitSlot ->
          val mapping = mappingApiService.getVisitSlotByNomisId(event.agencyVisitSlotId)
            .also { telemetry["dpsVisitSlotId"] = it.dpsId }
          val locationMapping = mappingApiService.getInternalLocationByNomisId(
            nomisLocationId = nomisVisitSlot.internalLocation.id,
          ).also { telemetry["dpsLocationId"] = it.dpsLocationId }

          dpsApiService.updateVisitSlot(
            prisonVisitSlotId = mapping.dpsId.toLong(),
            SyncUpdateVisitSlotRequest(
              dpsLocationId = UUID.fromString(locationMapping.dpsLocationId),
              updatedBy = nomisVisitSlot.audit.modifyUserId!!,
              updatedTime = nomisVisitSlot.audit.modifyDatetime!!,
              maxAdults = nomisVisitSlot.maxAdults,
              maxGroups = nomisVisitSlot.maxGroups,
            ),
          )
        }
      }
    }
  }

  suspend fun visitSlotDeleted(event: AgencyVisitSlotEvent) {
    val telemetryName = "officialvisits-visitslot-synchronisation-deleted"
    val telemetry = event.asTelemetry()

    mappingApiService.getVisitSlotByNomisIdOrNull(event.agencyVisitSlotId)?.also { mapping ->
      telemetry["dpsVisitSlotId"] = mapping.dpsId
      track(telemetryName, telemetry) {
        dpsApiService.deleteVisitSlot(mapping.dpsId.toLong())
        mappingApiService.deleteVisitSlotByNomisId(event.agencyVisitSlotId)
      }
    } ?: run {
      telemetryClient.trackEvent(
        "$telemetryName-ignored",
        telemetry,
      )
    }
  }

  private suspend fun tryToCreateMapping(
    mapping: VisitTimeSlotMappingDto,
    telemetry: Map<String, Any>,
  ) {
    try {
      createMapping(mapping)
    } catch (e: Exception) {
      log.error("Failed to create mapping for time slot id ${mapping.nomisPrisonId},${mapping.nomisDayOfWeek},${mapping.nomisSlotSequence}", e)
      queueService.sendMessage(
        messageType = OfficialVisitsSynchronisationMessageType.RETRY_SYNCHRONISATION_TIME_SLOT_MAPPING.name,
        synchronisationType = SynchronisationType.OFFICIAL_VISITS,
        message = mapping,
        telemetryAttributes = telemetry.valuesAsStrings(),
      )
    }
  }
  private suspend fun createMapping(
    mapping: VisitTimeSlotMappingDto,
  ) {
    mappingApiService.createTimeSlotMapping(
      mapping,
    )
      .takeIf { it.isError }?.also {
        with(it.errorResponse!!.moreInfo) {
          telemetryClient.trackEvent(
            "from-nomis-sync-officialvisits-duplicate",
            mapOf(
              "existingNomisPrisonId" to existing!!.nomisPrisonId,
              "existingNomisDayOfWeek" to existing.nomisDayOfWeek,
              "existingNomisSlotSequence" to existing.nomisSlotSequence,
              "existingDpsId" to existing.dpsId,
              "duplicateNomisPrisonId" to duplicate.nomisPrisonId,
              "duplicateNomisDayOfWeek" to duplicate.nomisDayOfWeek,
              "duplicateNomisSlotSequence" to duplicate.nomisSlotSequence,
              "duplicateDpsId" to duplicate.dpsId,
              "type" to "TIMESLOT",
            ),
          )
        }
      }
  }

  suspend fun retryCreateVisitTimeSlotMapping(retryMessage: InternalMessage<VisitTimeSlotMappingDto>) {
    createMapping(retryMessage.body)
      .also {
        telemetryClient.trackEvent(
          "officialvisits-timeslot-mapping-synchronisation-created",
          retryMessage.telemetryAttributes,
        )
      }
  }

  private suspend fun tryToCreateMapping(
    mapping: VisitSlotMappingDto,
    telemetry: Map<String, Any>,
  ) {
    try {
      createMapping(mapping)
    } catch (e: Exception) {
      log.error("Failed to create mapping for visit slot id ${mapping.nomisId}", e)
      queueService.sendMessage(
        messageType = OfficialVisitsSynchronisationMessageType.RETRY_SYNCHRONISATION_VISIT_SLOT_MAPPING.name,
        synchronisationType = SynchronisationType.OFFICIAL_VISITS,
        message = mapping,
        telemetryAttributes = telemetry.valuesAsStrings(),
      )
    }
  }
  private suspend fun createMapping(
    mapping: VisitSlotMappingDto,
  ) {
    mappingApiService.createVisitSlotMapping(
      mapping,
    )
      .takeIf { it.isError }?.also {
        with(it.errorResponse!!.moreInfo) {
          telemetryClient.trackEvent(
            "from-nomis-sync-officialvisits-duplicate",
            mapOf(
              "existingNomisId" to existing!!.nomisId,
              "existingDpsId" to existing.dpsId,
              "duplicateNomisId" to duplicate.nomisId,
              "duplicateDpsId" to duplicate.dpsId,
              "type" to "VISITSLOT",
            ),
          )
        }
      }
  }

  suspend fun retryCreateVisitSlotMapping(retryMessage: InternalMessage<VisitSlotMappingDto>) {
    createMapping(retryMessage.body)
      .also {
        telemetryClient.trackEvent(
          "officialvisits-visitslot-mapping-synchronisation-created",
          retryMessage.telemetryAttributes,
        )
      }
  }
}

private fun VisitTimeSlotResponse.toSyncCreateTimeSlotRequest() = SyncCreateTimeSlotRequest(
  prisonCode = this.prisonId,
  dayCode = this.dayOfWeek.asDpsApiDayOfWeek(),
  startTime = this.startTime,
  endTime = this.endTime,
  effectiveDate = this.effectiveDate,
  createdBy = this.audit.createUsername,
  createdTime = this.audit.createDatetime,
  expiryDate = this.expiryDate,
)
private fun VisitTimeSlotResponse.toSyncUpdateTimeSlotRequest() = SyncUpdateTimeSlotRequest(
  prisonCode = this.prisonId,
  dayCode = this.dayOfWeek.asDpsApiDayOfWeek(),
  startTime = this.startTime,
  endTime = this.endTime,
  effectiveDate = this.effectiveDate,
  updatedBy = this.audit.modifyUserId!!,
  updatedTime = this.audit.modifyDatetime!!,
  expiryDate = this.expiryDate,
)

fun AgencyVisitTimeEvent.asTelemetry() = mutableMapOf<String, Any>(
  "prisonId" to agencyLocationId,
  "nomisWeekDay" to weekDay,
  "nomisTimeslotSequence" to timeslotSequence,
)

fun AgencyVisitSlotEvent.asTelemetry() = mutableMapOf<String, Any>(
  "prisonId" to agencyLocationId,
  "nomisWeekDay" to weekDay,
  "nomisTimeslotSequence" to timeslotSequence,
  "nomisAgencyVisitSlotId" to agencyVisitSlotId.toString(),
)

private fun String.asNomisApiDayOfWeek(): VisitsConfigurationResourceApi.DayOfWeekGetVisitTimeSlot = when (this) {
  "MON" -> VisitsConfigurationResourceApi.DayOfWeekGetVisitTimeSlot.MON
  "TUE" -> VisitsConfigurationResourceApi.DayOfWeekGetVisitTimeSlot.TUE
  "WED" -> VisitsConfigurationResourceApi.DayOfWeekGetVisitTimeSlot.WED
  "THU" -> VisitsConfigurationResourceApi.DayOfWeekGetVisitTimeSlot.THU
  "FRI" -> VisitsConfigurationResourceApi.DayOfWeekGetVisitTimeSlot.FRI
  "SAT" -> VisitsConfigurationResourceApi.DayOfWeekGetVisitTimeSlot.SAT
  "SUN" -> VisitsConfigurationResourceApi.DayOfWeekGetVisitTimeSlot.SUN
  else -> throw IllegalArgumentException("Unknown day of week: $this")
}

private fun VisitTimeSlotResponse.DayOfWeek.asDpsApiDayOfWeek(): DayType = when (this) {
  VisitTimeSlotResponse.DayOfWeek.MON -> DayType.MON
  VisitTimeSlotResponse.DayOfWeek.TUE -> DayType.TUE
  VisitTimeSlotResponse.DayOfWeek.WED -> DayType.WED
  VisitTimeSlotResponse.DayOfWeek.THU -> DayType.THU
  VisitTimeSlotResponse.DayOfWeek.FRI -> DayType.FRI
  VisitTimeSlotResponse.DayOfWeek.SAT -> DayType.SAT
  VisitTimeSlotResponse.DayOfWeek.SUN -> DayType.SUN
}

private fun EventAudited.isFromDPSOfficialVisits() = this.auditExactMatchOrHasMissingAudit("${DPS_SYNC_AUDIT_MODULE}_OFFICIAL_VISITS")
