package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.EventAudited.Companion.DPS_SYNC_AUDIT_MODULE
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.TelemetryEnabled
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.track
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.VisitTimeSlotMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.api.VisitsConfigurationResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.VisitTimeSlotResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.DayType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.SyncCreateTimeSlotRequest

@Service
class VisitSlotsSynchronisationService(
  override val telemetryClient: TelemetryClient,
  private val mappingApiService: VisitSlotsMappingService,
  private val nomisApiService: VisitSlotsNomisApiService,
  private val dpsApiService: OfficialVisitsDpsApiService,
) : TelemetryEnabled {

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
              // TODO handle duplicates and failures
              mappingApiService.createTimeSlotMapping(
                VisitTimeSlotMappingDto(
                  dpsId = dpsTimeSlot.prisonTimeSlotId.toString(),
                  nomisPrisonId = nomisTimeSlot.prisonId,
                  nomisDayOfWeek = nomisTimeSlot.dayOfWeek.toString(),
                  nomisSlotSequence = nomisTimeSlot.timeSlotSequence,
                  mappingType = VisitTimeSlotMappingDto.MappingType.NOMIS_CREATED,
                ),
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
