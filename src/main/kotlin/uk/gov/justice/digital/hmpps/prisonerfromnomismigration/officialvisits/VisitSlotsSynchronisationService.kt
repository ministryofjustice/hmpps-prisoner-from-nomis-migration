package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.EventAudited.Companion.DPS_SYNC_AUDIT_MODULE
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.TelemetryEnabled
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.track
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.valuesAsStrings
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.VisitTimeSlotMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.api.VisitsConfigurationResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.VisitTimeSlotResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.DayType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.SyncCreateTimeSlotRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.InternalMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationType

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
    if (event.auditExactMatchOrHasMissingAudit("${DPS_SYNC_AUDIT_MODULE}_OFFICIAL_VISITS")) {
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
        track(telemetryName, event.asTelemetry()) {
          nomisApiService.getVisitTimeSlot(
            prisonId = event.agencyLocationId,
            dayOfWeek = event.weekDay.asNomisApiDayOfWeek(),
            timeSlotSequence = event.timeslotSequence,
          ).also { nomisTimeSlot ->
            dpsApiService.createTimeSlot(nomisTimeSlot.toSyncCreateTimeSlotRequest()).also { dpsTimeSlot ->
              tryToCreateMapping(
                VisitTimeSlotMappingDto(
                  dpsId = dpsTimeSlot.prisonTimeSlotId.toString(),
                  nomisPrisonId = nomisTimeSlot.prisonId,
                  nomisDayOfWeek = nomisTimeSlot.dayOfWeek.toString(),
                  nomisSlotSequence = nomisTimeSlot.timeSlotSequence,
                  mappingType = VisitTimeSlotMappingDto.MappingType.NOMIS_CREATED,
                ),
                telemetry = event.asTelemetry(),
              )
            }
          }
        }
      }
    }
  }
  fun visitTimeslotUpdated(event: AgencyVisitTimeEvent) = track("officialvisits-timeslot-synchronisation-updated", event.asTelemetry()) {}
  fun visitTimeslotDeleted(event: AgencyVisitTimeEvent) = track("officialvisits-timeslot-synchronisation-deleted", event.asTelemetry()) {}
  fun visitSlotAdded(event: AgencyVisitSlotEvent) = track("officialvisits-visitslot-synchronisation-created", event.asTelemetry()) {}
  fun visitSlotUpdated(event: AgencyVisitSlotEvent) = track("officialvisits-visitslot-synchronisation-updated", event.asTelemetry()) {}
  fun visitSlotDeleted(event: AgencyVisitSlotEvent) = track("officialvisits-visitslot-synchronisation-deleted", event.asTelemetry()) {}

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
