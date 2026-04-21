package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.taps

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.TelemetryEnabled
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.track
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.tryFetchParent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.valuesAsStrings
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.DirectionCode
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.ExternalMovementRetryMappingMessageTypes
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.ExternalMovementRetryMappingMessageTypes.RETRY_MAPPING_TEMPORARY_ABSENCE_SCHEDULED_MOVEMENT
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.ExternalMovementsMappingApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.MovementType.TAP
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.ScheduledMovementEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.Location
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.SyncAtAndBy
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.SyncWriteTapOccurrence
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ScheduledMovementSyncMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ScheduledMovementSyncMappingDto.MappingType.NOMIS_CREATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.TapScheduleOut
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.InternalMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationType
import java.util.*

private const val TELEMETRY_PREFIX: String = "${TAP_TELEMETRY_PREFIX}-scheduled-movement"

@Service
class TapScheduleService(
  override val telemetryClient: TelemetryClient,
  private val queueService: SynchronisationQueueService,
  private val mappingApiService: ExternalMovementsMappingApiService,
  private val nomisApiService: TapsNomisApiService,
  private val dpsApiService: TapDpsApiService,
) : TelemetryEnabled {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun scheduledMovementInserted(event: ScheduledMovementEvent) = when (event.eventMovementType) {
    TAP if (event.directionCode == DirectionCode.OUT) -> syncScheduledMovementTapOutInserted(event)
    else -> log.info("Ignoring insert of scheduled movement event ID ${event.eventId} with type ${event.eventMovementType} and direction ${event.directionCode} ")
  }

  suspend fun syncScheduledMovementTapOutInserted(event: ScheduledMovementEvent) {
    val (eventId, bookingId, prisonerNumber, _, directionCode) = event
    val telemetry = mutableMapOf<String, Any>(
      "offenderNo" to prisonerNumber,
      "bookingId" to bookingId,
      "nomisEventId" to eventId,
      "directionCode" to directionCode,
    )

    if (event.originatesInDps) {
      telemetryClient.trackEvent("${TELEMETRY_PREFIX}-inserted-skipped", telemetry)
      return
    }

    mappingApiService.getScheduledMovementMappingOrNull(eventId)
      ?.also { telemetryClient.trackEvent("${TELEMETRY_PREFIX}-inserted-ignored", telemetry) }
      ?: run {
        track("${TELEMETRY_PREFIX}-inserted", telemetry) {
          syncScheduledMovementTapOut(prisonerNumber, eventId, telemetry)
            ?.also { tryToCreateScheduledMovementMapping(it, telemetry) }
        }
      }
  }

  suspend fun syncScheduledMovementTapOut(
    prisonerNumber: String,
    eventId: Long,
    telemetry: MutableMap<String, Any>,
    existingMapping: ScheduledMovementSyncMappingDto? = null,
    onlyIfScheduled: Boolean = false,
  ): ScheduledMovementSyncMappingDto? = nomisApiService.getTapScheduleOut(prisonerNumber, eventId)
    .takeIf { !onlyIfScheduled || it.eventStatus == "SCH" }
    ?.also { telemetry["nomisApplicationId"] = it.tapApplicationId }
    ?.let { nomisSchedule ->
      val dpsAuthorisationId = tryFetchParent { getParentApplicationId(nomisSchedule.tapApplicationId) }
        .also { telemetry["dpsAuthorisationId"] = it }

      val dpsLocation = deriveDpsAddress(existingMapping, nomisSchedule)
      dpsLocation.uprn?.also { telemetry["dpsUprn"] = it }
      val dpsOccurrence = nomisSchedule.toDpsRequest(existingMapping?.dpsOccurrenceId, dpsLocation)
      val dpsOccurrenceId = dpsApiService.syncTapOccurrence(dpsAuthorisationId, dpsOccurrence).id
        .also { telemetry["dpsOccurrenceId"] = it }

      ScheduledMovementSyncMappingDto(
        prisonerNumber = prisonerNumber,
        bookingId = nomisSchedule.bookingId,
        nomisEventId = eventId,
        dpsOccurrenceId = dpsOccurrenceId,
        mappingType = NOMIS_CREATED,
        nomisAddressId = nomisSchedule.toAddressId ?: 0,
        nomisAddressOwnerClass = nomisSchedule.toAddressOwnerClass ?: "",
        dpsAddressText = dpsLocation.address ?: "",
        dpsUprn = dpsLocation.uprn,
        dpsDescription = dpsLocation.description,
        dpsPostcode = dpsLocation.postcode,
        eventTime = "${nomisSchedule.startTime}",
      )
        .also {
          telemetry["nomisAddressId"] = nomisSchedule.toAddressId ?: ""
          telemetry["nomisAddressOwnerClass"] = nomisSchedule.toAddressOwnerClass ?: ""
        }
    }
  suspend fun scheduledMovementUpdated(event: ScheduledMovementEvent) = when (event.eventMovementType) {
    TAP if (event.directionCode == DirectionCode.OUT) -> scheduledMovementTapOutUpdated(event)
    else -> log.info("Ignoring update of scheduled movement event ID ${event.eventId} with type ${event.eventMovementType} and direction ${event.directionCode} ")
  }

  suspend fun scheduledMovementTapOutUpdated(event: ScheduledMovementEvent) {
    val (eventId, bookingId, prisonerNumber, _, directionCode) = event
    val telemetry = mutableMapOf<String, Any>(
      "offenderNo" to prisonerNumber,
      "bookingId" to bookingId,
      "nomisEventId" to eventId,
      "directionCode" to directionCode,
    )

    if (event.originatesInDps) {
      telemetryClient.trackEvent("${TELEMETRY_PREFIX}-updated-skipped", telemetry)
      return
    }

    scheduledMovementTapOutUpdated(eventId, prisonerNumber, telemetry)
  }

  suspend fun scheduledMovementTapOutUpdated(eventId: Long, prisonerNumber: String, telemetry: MutableMap<String, Any>) {
    track("${TELEMETRY_PREFIX}-updated", telemetry) {
      val existingScheduleMapping = mappingApiService.getScheduledMovementMappingOrNull(eventId)
        ?.also { telemetry["dpsOccurrenceId"] = it.dpsOccurrenceId }
        ?: throw IllegalStateException("No mapping found when handling an update event for scheduled movement $eventId - hopefully messages are being processed out of order and this event will succeed on a retry once the create event is processed. Otherwise we need to understand why the original create event was never processed.")

      val newMapping = syncScheduledMovementTapOut(prisonerNumber, eventId, telemetry, existingScheduleMapping)
        ?: throw IllegalStateException("Could not find NOMIS scheduled movement when handling an update event for scheduled movement $eventId. Check if the schedule was deleted before this event was processed (by setting the TAP application back to pending), in which we can ignore the error.")
      if (newMapping.hasChanged(existingScheduleMapping)) {
        tryToUpdateScheduledMovementMapping(newMapping, telemetry)
      }
    }
  }

  suspend fun scheduledMovementDeleted(event: ScheduledMovementEvent) = when (event.eventMovementType) {
    TAP if (event.directionCode == DirectionCode.OUT) -> scheduledMovementTapOutDeleted(event)
    else -> log.info("Ignoring delete of scheduled movement event ID ${event.eventId} with type ${event.eventMovementType} and direction ${event.directionCode} ")
  }

  suspend fun scheduledMovementTapOutDeleted(event: ScheduledMovementEvent) {
    val (eventId, bookingId, prisonerNumber, _, directionCode) = event
    val telemetry = mutableMapOf<String, Any>(
      "offenderNo" to prisonerNumber,
      "bookingId" to bookingId,
      "nomisEventId" to eventId,
      "directionCode" to directionCode,
    )
    mappingApiService.getScheduledMovementMappingOrNull(eventId)?.also {
      track("${TELEMETRY_PREFIX}-deleted", telemetry) {
        telemetry["dpsOccurrenceId"] = it.dpsOccurrenceId
        dpsApiService.deleteTapOccurrence(it.dpsOccurrenceId)
        mappingApiService.deleteScheduledMovementMapping(eventId)
      }
    } ?: run { telemetryClient.trackEvent("${TELEMETRY_PREFIX}-deleted-ignored", telemetry) }
  }

  private suspend fun tryToCreateScheduledMovementMapping(mapping: ScheduledMovementSyncMappingDto, telemetry: MutableMap<String, Any>) {
    try {
      mappingApiService.createScheduledMovementMapping(mapping).takeIf { it.isError }?.also {
        with(it.errorResponse!!.moreInfo) {
          telemetryClient.trackEvent(
            "${TELEMETRY_PREFIX}-inserted-duplicate",
            mapOf(
              "existingOffenderNo" to existing!!.prisonerNumber,
              "existingBookingId" to existing.bookingId,
              "existingNomisEventId" to existing.nomisEventId,
              "existingDpsOccurrenceId" to existing.dpsOccurrenceId,
              "duplicateOffenderNo" to duplicate.prisonerNumber,
              "duplicateBookingId" to duplicate.bookingId,
              "duplicateNomisEventId" to duplicate.nomisEventId,
              "duplicateDpsOccurrenceId" to duplicate.dpsOccurrenceId,
            ),
          )
        }
      }
    } catch (e: Exception) {
      log.error("Failed to create mapping for temporary absence scheduled movement NOMIS id ${mapping.nomisEventId}", e)
      queueService.sendMessage(
        messageType = RETRY_MAPPING_TEMPORARY_ABSENCE_SCHEDULED_MOVEMENT.name,
        synchronisationType = SynchronisationType.EXTERNAL_MOVEMENTS,
        message = mapping,
        telemetryAttributes = telemetry.valuesAsStrings(),
      )
    }
  }

  suspend fun tryToUpdateScheduledMovementMapping(mapping: ScheduledMovementSyncMappingDto, telemetry: MutableMap<String, Any>) {
    try {
      mappingApiService.updateScheduledMovementMapping(mapping)
    } catch (e: Exception) {
      log.error("Failed to update mapping for temporary absence scheduled movement NOMIS id ${mapping.nomisEventId}", e)
      queueService.sendMessage(
        messageType = ExternalMovementRetryMappingMessageTypes.RETRY_UPDATE_MAPPING_TEMPORARY_ABSENCE_SCHEDULED_MOVEMENT.name,
        synchronisationType = SynchronisationType.EXTERNAL_MOVEMENTS,
        message = mapping,
        telemetryAttributes = telemetry.valuesAsStrings(),
      )
    }
  }

  suspend fun retryCreateScheduledMovementMapping(retryMessage: InternalMessage<ScheduledMovementSyncMappingDto>) {
    mappingApiService.createScheduledMovementMapping(
      retryMessage.body,
    ).also {
      telemetryClient.trackEvent(
        "${TELEMETRY_PREFIX}-mapping-retry-created",
        retryMessage.telemetryAttributes,
      )
    }
  }

  suspend fun retryUpdateScheduledMovementMapping(retryMessage: InternalMessage<ScheduledMovementSyncMappingDto>) {
    mappingApiService.updateScheduledMovementMapping(
      retryMessage.body,
    ).also {
      telemetryClient.trackEvent(
        "${TELEMETRY_PREFIX}-mapping-retry-updated",
        retryMessage.telemetryAttributes,
      )
    }
  }

  private suspend fun deriveDpsAddress(
    existingScheduleMapping: ScheduledMovementSyncMappingDto?,
    nomisSchedule: TapScheduleOut,
  ): Location {
    val hasNomisAddress = nomisSchedule.toAddressId != null && nomisSchedule.toAddressOwnerClass != null
    val newAddress = existingScheduleMapping == null ||
      (
        hasNomisAddress &&
          (
            existingScheduleMapping.nomisAddressId != nomisSchedule.toAddressId ||
              existingScheduleMapping.nomisAddressOwnerClass != nomisSchedule.toAddressOwnerClass ||
              existingScheduleMapping.dpsAddressText != nomisSchedule.toFullAddress ||
              existingScheduleMapping.dpsDescription != nomisSchedule.toAddressDescription ||
              existingScheduleMapping.dpsPostcode != nomisSchedule.toAddressPostcode
            )
        )

    return if (newAddress) {
      Location(nomisSchedule.toAddressDescription, nomisSchedule.toFullAddress ?: "", nomisSchedule.toAddressPostcode, null)
    } else {
      // NOMIS address is unchanged, use DPS address as saved on scheduled mapping
      Location(existingScheduleMapping.dpsDescription, existingScheduleMapping.dpsAddressText, existingScheduleMapping.dpsPostcode, existingScheduleMapping.dpsUprn)
    }
  }

  private suspend fun getParentApplicationId(nomisApplicationId: Long): UUID? = mappingApiService.getApplicationMappingOrNull(nomisApplicationId)
    ?.dpsMovementApplicationId

  private fun ScheduledMovementSyncMappingDto.hasChanged(original: ScheduledMovementSyncMappingDto) = this.prisonerNumber != original.prisonerNumber ||
    this.bookingId != original.bookingId ||
    this.nomisEventId != original.nomisEventId ||
    this.dpsOccurrenceId != original.dpsOccurrenceId ||
    this.nomisAddressId != original.nomisAddressId ||
    this.nomisAddressOwnerClass != original.nomisAddressOwnerClass ||
    this.dpsAddressText != original.dpsAddressText ||
    this.eventTime != original.eventTime
}

fun TapScheduleOut.toDpsRequest(id: UUID? = null, dpsLocation: Location) = SyncWriteTapOccurrence(
  id = id,
  start = startTime,
  end = returnTime,
  location = dpsLocation,
  absenceTypeCode = tapAbsenceType,
  absenceSubTypeCode = tapSubType,
  absenceReasonCode = eventSubType,
  accompaniedByCode = escort ?: DEFAULT_ESCORT_CODE,
  transportCode = transportType ?: DEFAULT_TRANSPORT_TYPE,
  comments = comment,
  created = SyncAtAndBy(at = audit.createDatetime, by = audit.createUsername),
  updated = audit.modifyDatetime?.let { SyncAtAndBy(at = audit.modifyDatetime, by = audit.modifyUserId!!) },
  isCancelled = eventStatus == "CANC",
  legacyId = eventId,
  contactInformation = this.contactPersonName,
)
