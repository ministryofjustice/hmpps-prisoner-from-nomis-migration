package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.ParentEntityNotFoundRetry
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.TelemetryEnabled
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.track
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.valuesAsStrings
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.ExternalMovementRetryMappingMessageTypes.RETRY_MAPPING_TEMPORARY_ABSENCE_APPLICATION
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.ExternalMovementRetryMappingMessageTypes.RETRY_MAPPING_TEMPORARY_ABSENCE_EXTERNAL_MOVEMENT
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.ExternalMovementRetryMappingMessageTypes.RETRY_MAPPING_TEMPORARY_ABSENCE_SCHEDULED_MOVEMENT
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.ExternalMovementRetryMappingMessageTypes.RETRY_UPDATE_MAPPING_TEMPORARY_ABSENCE_EXTERNAL_MOVEMENT
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.MovementType.TAP
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.Location
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.SyncAtAndBy
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.SyncWriteTapAuthorisation
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.SyncWriteTapMovement
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.SyncWriteTapOccurrence
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ExternalMovementSyncMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ScheduledMovementSyncMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TemporaryAbsenceApplicationSyncMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TemporaryAbsenceApplicationSyncMappingDto.MappingType.NOMIS_CREATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.ScheduledTemporaryAbsenceResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.TemporaryAbsenceApplicationResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.TemporaryAbsenceResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.TemporaryAbsenceReturnResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.InternalMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationType
import java.util.*
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ScheduledMovementSyncMappingDto.MappingType.NOMIS_CREATED as SCHEDULED_MOVEMENT_NOMIS_CREATED

private const val TELEMETRY_PREFIX: String = "temporary-absence-sync"
const val DEFAULT_ESCORT_CODE = "U"
const val DEFAULT_TRANSPORT_TYPE = "TNR"

@Service
class ExternalMovementsSyncService(
  override val telemetryClient: TelemetryClient,
  private val queueService: SynchronisationQueueService,
  private val mappingApiService: ExternalMovementsMappingApiService,
  private val nomisApiService: ExternalMovementsNomisApiService,
  private val dpsApiService: ExternalMovementsDpsApiService,
) : TelemetryEnabled {
  suspend fun movementApplicationInserted(event: MovementApplicationEvent) {
    val (nomisApplicationId, bookingId, prisonerNumber) = event
    val telemetry = mutableMapOf<String, Any>(
      "offenderNo" to prisonerNumber,
      "bookingId" to bookingId,
      "nomisApplicationId" to nomisApplicationId,
    )

    if (event.originatesInDps) {
      telemetryClient.trackEvent("$TELEMETRY_PREFIX-application-inserted-skipped", telemetry)
      return
    }

    mappingApiService.getApplicationMappingOrNull(nomisApplicationId)
      ?.also { telemetryClient.trackEvent("$TELEMETRY_PREFIX-application-inserted-ignored", telemetry) }
      ?: run {
        track("$TELEMETRY_PREFIX-application-inserted", telemetry) {
          nomisApiService.getTemporaryAbsenceApplication(prisonerNumber, nomisApplicationId)
            .also {
              val dpsApplicationId = dpsApiService.syncTapAuthorisation(prisonerNumber, it.toDpsRequest())
                .id
                .also { telemetry["dpsAuthorisationId"] = it }
              val mapping = TemporaryAbsenceApplicationSyncMappingDto(prisonerNumber, bookingId, nomisApplicationId, dpsApplicationId, NOMIS_CREATED)
              tryToCreateApplicationMapping(mapping, telemetry)
            }
        }
      }
  }

  suspend fun movementApplicationUpdated(event: MovementApplicationEvent) {
    val (nomisApplicationId, bookingId, prisonerNumber) = event
    val telemetry = mutableMapOf<String, Any>(
      "offenderNo" to prisonerNumber,
      "bookingId" to bookingId,
      "nomisApplicationId" to nomisApplicationId,
    )

    if (event.originatesInDps) {
      telemetryClient.trackEvent("$TELEMETRY_PREFIX-application-updated-skipped", telemetry)
      return
    }

    track("$TELEMETRY_PREFIX-application-updated", telemetry) {
      val dpsApplicationId = mappingApiService.getApplicationMappingOrNull(nomisApplicationId)?.dpsMovementApplicationId
        ?.also { telemetry["dpsAuthorisationId"] = it }
        ?: throw IllegalStateException("No mapping found when handling an update event for TAP application $nomisApplicationId - hopefully messages are being processed out of order and this event will succeed on a retry once the create event is processed. Otherwise we need to understand why the original create event was never processed.")

      val nomisApplication = nomisApiService.getTemporaryAbsenceApplication(prisonerNumber, nomisApplicationId)
      dpsApiService.syncTapAuthorisation(prisonerNumber, nomisApplication.toDpsRequest(dpsApplicationId))
    }
  }

  suspend fun movementApplicationDeleted(event: MovementApplicationEvent) {
    val (nomisApplicationId, bookingId, prisonerNumber) = event
    val telemetry = mutableMapOf<String, Any>(
      "offenderNo" to prisonerNumber,
      "bookingId" to bookingId,
      "nomisApplicationId" to nomisApplicationId,
    )
    mappingApiService.getApplicationMappingOrNull(nomisApplicationId)?.also {
      track("$TELEMETRY_PREFIX-application-deleted", telemetry) {
        telemetry["dpsAuthorisationId"] = it.dpsMovementApplicationId
        dpsApiService.deleteTapAuthorisation(it.dpsMovementApplicationId)
        mappingApiService.deleteApplicationMapping(nomisApplicationId)
      }
    } ?: run { telemetryClient.trackEvent("$TELEMETRY_PREFIX-application-deleted-ignored", telemetry) }
  }

  private suspend fun requireParentApplicationExists(nomisApplicationId: Long): UUID = getParentApplicationId(nomisApplicationId)
    ?: throw ParentEntityNotFoundRetry("Application $nomisApplicationId not created yet so children cannot be processed")

  private suspend fun getParentApplicationId(nomisApplicationId: Long): UUID? = mappingApiService.getApplicationMappingOrNull(nomisApplicationId)
    ?.dpsMovementApplicationId

  private suspend fun requireParentScheduleExists(nomisEventId: Long) = getParentScheduledId(nomisEventId)
    ?: throw ParentEntityNotFoundRetry("Scheduled event ID $nomisEventId not created yet so children cannot be processed")

  private suspend fun getParentScheduledId(nomisEventId: Long): UUID? = mappingApiService.getScheduledMovementMappingOrNull(nomisEventId)
    ?.dpsOccurrenceId

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
      telemetryClient.trackEvent("$TELEMETRY_PREFIX-scheduled-movement-inserted-skipped", telemetry)
      return
    }

    mappingApiService.getScheduledMovementMappingOrNull(eventId)
      ?.also { telemetryClient.trackEvent("$TELEMETRY_PREFIX-scheduled-movement-inserted-ignored", telemetry) }
      ?: run {
        track("$TELEMETRY_PREFIX-scheduled-movement-inserted", telemetry) {
          syncScheduledMovementTapOut(prisonerNumber, eventId, telemetry)
            ?.also { tryToCreateScheduledMovementMapping(it, telemetry) }
        }
      }
  }

  private suspend fun syncScheduledMovementTapOut(
    prisonerNumber: String,
    eventId: Long,
    telemetry: MutableMap<String, Any>,
    existingMapping: ScheduledMovementSyncMappingDto? = null,
    onlyIfScheduled: Boolean = false,
  ): ScheduledMovementSyncMappingDto? = nomisApiService.getTemporaryAbsenceScheduledMovement(prisonerNumber, eventId)
    .takeIf { !onlyIfScheduled || it.eventStatus == "SCH" }
    ?.also { telemetry["nomisApplicationId"] = it.movementApplicationId }
    ?.let { nomisSchedule ->
      val dpsAuthorisationId = requireParentApplicationExists(nomisSchedule.movementApplicationId)
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
        mappingType = SCHEDULED_MOVEMENT_NOMIS_CREATED,
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

  private suspend fun deriveDpsAddress(
    existingScheduleMapping: ScheduledMovementSyncMappingDto?,
    nomisSchedule: ScheduledTemporaryAbsenceResponse,
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
      telemetryClient.trackEvent("$TELEMETRY_PREFIX-scheduled-movement-updated-skipped", telemetry)
      return
    }

    scheduledMovementTapOutUpdated(eventId, prisonerNumber, telemetry)
  }

  suspend fun scheduledMovementTapOutUpdated(eventId: Long, prisonerNumber: String, telemetry: MutableMap<String, Any>) {
    track("$TELEMETRY_PREFIX-scheduled-movement-updated", telemetry) {
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
      track("$TELEMETRY_PREFIX-scheduled-movement-deleted", telemetry) {
        telemetry["dpsOccurrenceId"] = it.dpsOccurrenceId
        dpsApiService.deleteTapOccurrence(it.dpsOccurrenceId)
        mappingApiService.deleteScheduledMovementMapping(eventId)
      }
    } ?: run { telemetryClient.trackEvent("$TELEMETRY_PREFIX-scheduled-movement-deleted-ignored", telemetry) }
  }

  private suspend fun tryToCreateApplicationMapping(mapping: TemporaryAbsenceApplicationSyncMappingDto, telemetry: MutableMap<String, Any>) {
    try {
      mappingApiService.createApplicationMapping(mapping).takeIf { it.isError }?.also {
        with(it.errorResponse!!.moreInfo) {
          telemetryClient.trackEvent(
            "$TELEMETRY_PREFIX-application-inserted-duplicate",
            mapOf(
              "existingOffenderNo" to existing!!.prisonerNumber,
              "existingBookingId" to existing.bookingId,
              "existingNomisApplicationId" to existing.nomisMovementApplicationId,
              "existingDpsApplicationId" to existing.dpsMovementApplicationId,
              "duplicateOffenderNo" to duplicate.prisonerNumber,
              "duplicateBookingId" to duplicate.bookingId,
              "duplicateNomisApplicationId" to duplicate.nomisMovementApplicationId,
              "duplicateDpsApplicationId" to duplicate.dpsMovementApplicationId,
            ),
          )
        }
      }
    } catch (e: Exception) {
      log.error("Failed to create mapping for temporary absence application NOMIS id ${mapping.nomisMovementApplicationId}", e)
      queueService.sendMessage(
        messageType = RETRY_MAPPING_TEMPORARY_ABSENCE_APPLICATION.name,
        synchronisationType = SynchronisationType.EXTERNAL_MOVEMENTS,
        message = mapping,
        telemetryAttributes = telemetry.valuesAsStrings(),
      )
    }
  }

  private suspend fun tryToCreateScheduledMovementMapping(mapping: ScheduledMovementSyncMappingDto, telemetry: MutableMap<String, Any>) {
    try {
      mappingApiService.createScheduledMovementMapping(mapping).takeIf { it.isError }?.also {
        with(it.errorResponse!!.moreInfo) {
          telemetryClient.trackEvent(
            "$TELEMETRY_PREFIX-scheduled-movement-inserted-duplicate",
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

  private suspend fun tryToUpdateScheduledMovementMapping(mapping: ScheduledMovementSyncMappingDto, telemetry: MutableMap<String, Any>) {
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

  suspend fun retryCreateApplicationMapping(retryMessage: InternalMessage<TemporaryAbsenceApplicationSyncMappingDto>) {
    mappingApiService.createApplicationMapping(
      retryMessage.body,
    ).also {
      telemetryClient.trackEvent(
        "$TELEMETRY_PREFIX-application-mapping-retry-created",
        retryMessage.telemetryAttributes,
      )
    }
  }

  suspend fun retryCreateScheduledMovementMapping(retryMessage: InternalMessage<ScheduledMovementSyncMappingDto>) {
    mappingApiService.createScheduledMovementMapping(
      retryMessage.body,
    ).also {
      telemetryClient.trackEvent(
        "$TELEMETRY_PREFIX-scheduled-movement-mapping-retry-created",
        retryMessage.telemetryAttributes,
      )
    }
  }

  suspend fun retryUpdateScheduledMovementMapping(retryMessage: InternalMessage<ScheduledMovementSyncMappingDto>) {
    mappingApiService.updateScheduledMovementMapping(
      retryMessage.body,
    ).also {
      telemetryClient.trackEvent(
        "$TELEMETRY_PREFIX-scheduled-movement-mapping-retry-updated",
        retryMessage.telemetryAttributes,
      )
    }
  }

  suspend fun externalMovementChanged(event: ExternalMovementEvent) = when (event.movementType) {
    TAP if event.recordInserted -> externalMovementTapInserted(event)
    TAP if event.recordDeleted -> externalMovementTapDeleted(event)
    TAP -> externalMovementTapUpdated(event)
    else -> log.info(("Ignoring external movement changed event with type ${event.movementType}, inserted=${event.recordInserted}, deleted=${event.recordDeleted}}"))
  }

  suspend fun externalMovementTapInserted(event: ExternalMovementEvent) {
    val (bookingId, prisonerNumber, movementSeq, _, directionCode) = event
    val telemetry = mutableMapOf<String, Any>(
      "offenderNo" to prisonerNumber!!,
      "bookingId" to bookingId,
      "movementSeq" to movementSeq,
      "directionCode" to directionCode,
    )

    if (event.originatesInDps) {
      telemetryClient.trackEvent("$TELEMETRY_PREFIX-external-movement-inserted-skipped", telemetry)
      return
    }

    mappingApiService.getExternalMovementMappingOrNull(bookingId, movementSeq)
      ?.also {
        telemetry["dpsMovementId"] = it.dpsMovementId
        telemetryClient.trackEvent("$TELEMETRY_PREFIX-external-movement-inserted-ignored", telemetry)
      }
      ?: run {
        track("$TELEMETRY_PREFIX-external-movement-inserted", telemetry) {
          val mapping = when (directionCode) {
            DirectionCode.OUT -> syncExternalMovementTapOut(prisonerNumber, bookingId, movementSeq, telemetry)
            DirectionCode.IN -> syncExternalMovementTapIn(prisonerNumber, bookingId, movementSeq, telemetry)
          }
          tryToCreateExternalMovementMapping(mapping, telemetry)
        }
      }
  }

  private suspend fun syncExternalMovementTapOut(
    prisonerNumber: String,
    bookingId: Long,
    movementSeq: Int,
    telemetry: MutableMap<String, Any>,
    existingMapping: ExternalMovementSyncMappingDto? = null,
  ): ExternalMovementSyncMappingDto = nomisApiService.getTemporaryAbsenceMovement(prisonerNumber, bookingId, movementSeq)
    .also {
      it.scheduledTemporaryAbsenceId?.run { telemetry["nomisScheduledEventId"] = this }
      it.movementApplicationId?.run { telemetry["nomisApplicationId"] = this }
      it.movementApplicationId?.run { requireParentApplicationExists(it.movementApplicationId) }
    }
    .let { nomisMovement ->
      val dpsOccurrenceId = nomisMovement.scheduledTemporaryAbsenceId?.let { requireParentScheduleExists(it) }
        ?.also { telemetry["dpsOccurrenceId"] = it }

      val dpsLocation = deriveDpsAddress(existingMapping, nomisMovement.toAddressId, nomisMovement.toAddressOwnerClass, nomisMovement.toFullAddress, nomisMovement.toAddressDescription, nomisMovement.toAddressPostcode)
      dpsLocation.uprn?.also { telemetry["dpsUprn"] = it }
      val dpsMovement = nomisMovement.toDpsRequest(existingMapping?.dpsMovementId, dpsOccurrenceId, dpsLocation)
      val dpsMovementId = dpsApiService.syncTapMovement(prisonerNumber, dpsMovement).id
        .also { telemetry["dpsMovementId"] = it }

      ExternalMovementSyncMappingDto(
        prisonerNumber = prisonerNumber,
        bookingId = bookingId,
        nomisMovementSeq = movementSeq,
        dpsMovementId = dpsMovementId,
        mappingType = ExternalMovementSyncMappingDto.MappingType.NOMIS_CREATED,
        dpsAddressText = dpsLocation.address ?: "",
        dpsUprn = dpsLocation.uprn,
        dpsDescription = dpsLocation.description,
        dpsPostcode = dpsLocation.postcode,
        nomisAddressId = nomisMovement.toAddressId,
        nomisAddressOwnerClass = nomisMovement.toAddressOwnerClass,
      )
        .also {
          if (nomisMovement.toAddressId != null) telemetry["nomisAddressId"] = nomisMovement.toAddressId
          if (nomisMovement.toAddressOwnerClass != null) telemetry["nomisAddressOwnerClass"] = nomisMovement.toAddressOwnerClass
        }
    }

  private suspend fun syncExternalMovementTapIn(
    prisonerNumber: String,
    bookingId: Long,
    movementSeq: Int,
    telemetry: MutableMap<String, Any>,
    existingMapping: ExternalMovementSyncMappingDto? = null,
  ): ExternalMovementSyncMappingDto = nomisApiService.getTemporaryAbsenceReturnMovement(prisonerNumber, bookingId, movementSeq)
    .also {
      it.scheduledTemporaryAbsenceId?.run { telemetry["nomisScheduledParentEventId"] = this }
      it.scheduledTemporaryAbsenceReturnId?.run { telemetry["nomisScheduledEventId"] = this }
      it.movementApplicationId?.run { telemetry["nomisApplicationId"] = this }
      it.movementApplicationId?.run { requireParentApplicationExists(it.movementApplicationId) }
    }
    .let { nomisMovement ->
      val dpsOccurrenceId = nomisMovement.scheduledTemporaryAbsenceId?.let { requireParentScheduleExists(it) }
        ?.also { telemetry["dpsOccurrenceId"] = it }

      val dpsLocation = deriveDpsAddress(existingMapping, nomisMovement.fromAddressId, nomisMovement.fromAddressOwnerClass, nomisMovement.fromFullAddress, nomisMovement.fromAddressDescription, nomisMovement.fromAddressPostcode)
      dpsLocation.uprn?.also { telemetry["dpsUprn"] = it }
      val dpsMovement = nomisMovement.toDpsRequest(existingMapping?.dpsMovementId, dpsOccurrenceId, dpsLocation)
      val dpsMovementId = dpsApiService.syncTapMovement(prisonerNumber, dpsMovement).id
        .also { telemetry["dpsMovementId"] = it }

      ExternalMovementSyncMappingDto(
        prisonerNumber = prisonerNumber,
        bookingId = bookingId,
        nomisMovementSeq = movementSeq,
        dpsMovementId = dpsMovementId,
        mappingType = ExternalMovementSyncMappingDto.MappingType.NOMIS_CREATED,
        dpsAddressText = dpsLocation.address ?: "",
        dpsUprn = dpsLocation.uprn,
        dpsDescription = dpsLocation.description,
        dpsPostcode = dpsLocation.postcode,
        nomisAddressId = nomisMovement.fromAddressId,
        nomisAddressOwnerClass = nomisMovement.fromAddressOwnerClass,
      )
        .also {
          if (nomisMovement.fromAddressId != null) telemetry["nomisAddressId"] = nomisMovement.fromAddressId
          if (nomisMovement.fromAddressOwnerClass != null) telemetry["nomisAddressOwnerClass"] = nomisMovement.fromAddressOwnerClass
        }
    }

  suspend fun externalMovementTapUpdated(event: ExternalMovementEvent) {
    val (bookingId, prisonerNumber, movementSeq, _, directionCode) = event
    val telemetry = mutableMapOf<String, Any>(
      "offenderNo" to prisonerNumber!!,
      "bookingId" to bookingId,
      "movementSeq" to movementSeq,
      "directionCode" to directionCode,
    )

    if (event.originatesInDps) {
      telemetryClient.trackEvent("$TELEMETRY_PREFIX-external-movement-updated-skipped", telemetry)
      return
    }

    track("$TELEMETRY_PREFIX-external-movement-updated", telemetry) {
      val existingMapping = mappingApiService.getExternalMovementMappingOrNull(bookingId, movementSeq)
        ?.also { telemetry["dpsMovementId"] = it.dpsMovementId }
        ?: throw IllegalStateException("No mapping found when handling an update event for movement $bookingId/$movementSeq - hopefully messages are being processed out of order and this event will succeed on a retry once the create event is processed. Otherwise we need to understand why the original create event was never processed.")

      val newMapping = when (directionCode) {
        DirectionCode.OUT -> syncExternalMovementTapOut(prisonerNumber, bookingId, movementSeq, telemetry, existingMapping)
        DirectionCode.IN -> syncExternalMovementTapIn(prisonerNumber, bookingId, movementSeq, telemetry, existingMapping)
      }

      if (newMapping.hasChanged(existingMapping)) {
        tryToUpdateExternalMovementMapping(newMapping, telemetry)
      }
    }
  }

  private suspend fun deriveDpsAddress(
    existingMovementMapping: ExternalMovementSyncMappingDto?,
    nomisAddressId: Long?,
    nomisAddressOwnerClass: String?,
    nomisAddress: String?,
    nomisAddressDescription: String? = null,
    nomisAddressPostcode: String? = null,
  ): Location {
    val hasNomisAddress = nomisAddressId != null && nomisAddressOwnerClass != null
    val addressHasChanged = existingMovementMapping == null ||
      (hasNomisAddress && existingMovementMapping.nomisAddressId != nomisAddressId) ||
      (!hasNomisAddress && existingMovementMapping.dpsAddressText != nomisAddress)

    return if (addressHasChanged && hasNomisAddress) {
      Location(nomisAddressDescription, nomisAddress ?: "", nomisAddressPostcode, null)
    } else if (addressHasChanged) {
      // There is no address in NOMIS so this must be a City and all we have is the NOMIS address text
      Location(null, nomisAddress, null, null)
    } else {
      // NOMIS address is unchanged, use DPS address as saved on the movement mapping
      Location(existingMovementMapping.dpsDescription, existingMovementMapping.dpsAddressText, existingMovementMapping.dpsPostcode, existingMovementMapping.dpsUprn)
    }
  }

  suspend fun externalMovementTapDeleted(event: ExternalMovementEvent) {
    val (bookingId, prisonerNumber, movementSeq, _, directionCode) = event
    val telemetry = mutableMapOf<String, Any>(
      "offenderNo" to prisonerNumber!!,
      "bookingId" to bookingId,
      "movementSeq" to movementSeq,
      "directionCode" to directionCode,
    )
    mappingApiService.getExternalMovementMappingOrNull(bookingId, movementSeq)?.also {
      track("$TELEMETRY_PREFIX-external-movement-deleted", telemetry) {
        telemetry["dpsMovementId"] = it.dpsMovementId
        dpsApiService.deleteTapMovement(it.dpsMovementId)
        mappingApiService.deleteExternalMovementMapping(bookingId, movementSeq)
      }
    } ?: run { telemetryClient.trackEvent("$TELEMETRY_PREFIX-external-movement-deleted-ignored", telemetry) }
  }

  private suspend fun tryToCreateExternalMovementMapping(mapping: ExternalMovementSyncMappingDto, telemetry: MutableMap<String, Any>) {
    try {
      mappingApiService.createExternalMovementMapping(mapping).takeIf { it.isError }?.also {
        with(it.errorResponse!!.moreInfo) {
          telemetryClient.trackEvent(
            "$TELEMETRY_PREFIX-external-movement-inserted-duplicate",
            mapOf(
              "existingOffenderNo" to existing!!.prisonerNumber,
              "existingBookingId" to existing.bookingId,
              "existingMovementSeq" to existing.nomisMovementSeq,
              "existingDpsMovementId" to existing.dpsMovementId,
              "duplicateOffenderNo" to duplicate.prisonerNumber,
              "duplicateBookingId" to duplicate.bookingId,
              "duplicateMovementSeq" to duplicate.nomisMovementSeq,
              "duplicateDpsMovementId" to duplicate.dpsMovementId,
            ),
          )
        }
      }
    } catch (e: Exception) {
      log.error("Failed to create mapping for temporary absence external movement NOMIS id ${mapping.bookingId}/${mapping.nomisMovementSeq}", e)
      queueService.sendMessage(
        messageType = RETRY_MAPPING_TEMPORARY_ABSENCE_EXTERNAL_MOVEMENT.name,
        synchronisationType = SynchronisationType.EXTERNAL_MOVEMENTS,
        message = mapping,
        telemetryAttributes = telemetry.valuesAsStrings(),
      )
    }
  }

  suspend fun retryCreateExternalMovementMapping(retryMessage: InternalMessage<ExternalMovementSyncMappingDto>) {
    mappingApiService.createExternalMovementMapping(
      retryMessage.body,
    ).also {
      telemetryClient.trackEvent(
        "$TELEMETRY_PREFIX-external-movement-mapping-retry-created",
        retryMessage.telemetryAttributes,
      )
    }
  }

  private suspend fun tryToUpdateExternalMovementMapping(mapping: ExternalMovementSyncMappingDto, telemetry: MutableMap<String, Any>) {
    try {
      mappingApiService.updateExternalMovementMapping(mapping)
    } catch (e: Exception) {
      log.error("Failed to update mapping for temporary absence external movement NOMIS id ${mapping.bookingId}/${mapping.nomisMovementSeq}", e)
      queueService.sendMessage(
        messageType = RETRY_UPDATE_MAPPING_TEMPORARY_ABSENCE_EXTERNAL_MOVEMENT.name,
        synchronisationType = SynchronisationType.EXTERNAL_MOVEMENTS,
        message = mapping,
        telemetryAttributes = telemetry.valuesAsStrings(),
      )
    }
  }

  suspend fun retryUpdateExternalMovementMapping(retryMessage: InternalMessage<ExternalMovementSyncMappingDto>) {
    mappingApiService.updateExternalMovementMapping(
      retryMessage.body,
    ).also {
      telemetryClient.trackEvent(
        "$TELEMETRY_PREFIX-external-movement-mapping-updated",
        retryMessage.telemetryAttributes,
      )
    }
  }

  suspend fun offenderAddressUpdated(offenderAddressUpdatedEvent: OffenderAddressUpdatedEvent) = addressDetailsUpdated(offenderAddressUpdatedEvent.addressId, "OFF")

  suspend fun corporateAddressUpdated(corporateAddressUpdatedEvent: CorporateAddressUpdatedEvent) = addressDetailsUpdated(corporateAddressUpdatedEvent.addressId, "CORP")

  suspend fun agencyAddressUpdated(agencyAddressUpdatedEvent: AgencyAddressUpdatedEvent) = addressDetailsUpdated(agencyAddressUpdatedEvent.addressId, "AGY")

  private suspend fun addressDetailsUpdated(addressId: Long, addressOwnerClass: String) {
    val addressUpdateTelemetry = mutableMapOf<String, Any>("nomisAddressId" to "$addressId", "nomisAddressOwnerClass" to addressOwnerClass)

    track("$TELEMETRY_PREFIX-address-updated", addressUpdateTelemetry) {
      val affectedSchedules = mappingApiService.findScheduledMovementMappingsForAddress(addressId)
        .also { addressUpdateTelemetry["nomisEventIds"] = it.scheduleMappings.map { it.nomisEventId }.toString() }
        .also { addressUpdateTelemetry["dpsOccurrenceIds"] = it.scheduleMappings.map { it.dpsOccurrenceId }.toString() }

      affectedSchedules.scheduleMappings.forEach { scheduleMapping ->
        val syncTelemetry = mutableMapOf<String, Any>(
          "offenderNo" to scheduleMapping.prisonerNumber,
          "bookingId" to scheduleMapping.bookingId,
          "nomisEventId" to scheduleMapping.nomisEventId,
          "directionCode" to "OUT",
        )
        track("$TELEMETRY_PREFIX-scheduled-movement-updated", syncTelemetry) {
          syncScheduledMovementTapOut(scheduleMapping.prisonerNumber, scheduleMapping.nomisEventId, syncTelemetry, scheduleMapping, onlyIfScheduled = true)
            ?.also { tryToUpdateScheduledMovementMapping(it, syncTelemetry) }
            ?: also { syncTelemetry["ignored"] = true }
        }
      }
    }
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}

private fun TemporaryAbsenceApplicationResponse.toDpsRequest(id: UUID? = null) = SyncWriteTapAuthorisation(
  id = id,
  prisonCode = prisonId,
  statusCode = applicationStatus.toDpsAuthorisationStatusCode(),
  absenceTypeCode = temporaryAbsenceType,
  absenceSubTypeCode = temporaryAbsenceSubType,
  absenceReasonCode = eventSubType,
  accompaniedByCode = escortCode ?: DEFAULT_ESCORT_CODE,
  repeat = applicationType == "REPEATING",
  start = fromDate,
  end = toDate,
  comments = comment,
  created = SyncAtAndBy(audit.createDatetime, audit.createUsername),
  updated = audit.modifyDatetime?.let { SyncAtAndBy(audit.modifyDatetime, audit.modifyUserId!!) },
  legacyId = movementApplicationId,
  transportCode = transportType ?: DEFAULT_TRANSPORT_TYPE,
)

fun String.toDpsAuthorisationStatusCode() = when (this) {
  "PEN" -> "PENDING"
  "APP-SCH", "APP-UNSCH" -> "APPROVED"
  "DEN" -> "DENIED"
  "CANC" -> "CANCELLED"
  else -> throw IllegalArgumentException("Unknown temporary absence status code: $this")
}

fun ScheduledTemporaryAbsenceResponse.toDpsRequest(id: UUID? = null, dpsLocation: Location) = SyncWriteTapOccurrence(
  id = id,
  start = startTime,
  end = returnTime,
  location = dpsLocation,
  absenceTypeCode = temporaryAbsenceType,
  absenceSubTypeCode = temporaryAbsenceSubType,
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

private fun TemporaryAbsenceResponse.toDpsRequest(id: UUID? = null, occurrenceId: UUID? = null, dpsLocation: Location) = SyncWriteTapMovement(
  id = id,
  occurrenceId = occurrenceId,
  occurredAt = movementTime,
  direction = SyncWriteTapMovement.Direction.OUT,
  absenceReasonCode = movementReason,
  location = dpsLocation,
  accompaniedByCode = escort ?: DEFAULT_ESCORT_CODE,
  accompaniedByComments = escortText,
  comments = commentText,
  created = SyncAtAndBy(audit.createDatetime, audit.createUsername),
  updated = audit.modifyDatetime?.let { SyncAtAndBy(audit.modifyDatetime, audit.modifyUserId!!) },
  legacyId = "${bookingId}_$sequence",
  prisonCode = fromPrison,
)

private fun TemporaryAbsenceReturnResponse.toDpsRequest(id: UUID? = null, occurrenceId: UUID? = null, dpsLocation: Location) = SyncWriteTapMovement(
  id = id,
  occurrenceId = occurrenceId,
  occurredAt = movementTime,
  direction = SyncWriteTapMovement.Direction.IN,
  absenceReasonCode = movementReason,
  location = dpsLocation,
  accompaniedByCode = escort ?: DEFAULT_ESCORT_CODE,
  accompaniedByComments = escortText,
  comments = commentText,
  created = SyncAtAndBy(audit.createDatetime, audit.createUsername),
  updated = audit.modifyDatetime?.let { SyncAtAndBy(audit.modifyDatetime, audit.modifyUserId!!) },
  legacyId = "${bookingId}_$sequence",
  prisonCode = toPrison,
)

private fun ScheduledMovementSyncMappingDto.hasChanged(original: ScheduledMovementSyncMappingDto) = this.prisonerNumber != original.prisonerNumber ||
  this.bookingId != original.bookingId ||
  this.nomisEventId != original.nomisEventId ||
  this.dpsOccurrenceId != original.dpsOccurrenceId ||
  this.nomisAddressId != original.nomisAddressId ||
  this.nomisAddressOwnerClass != original.nomisAddressOwnerClass ||
  this.dpsAddressText != original.dpsAddressText ||
  this.eventTime != original.eventTime

private fun ExternalMovementSyncMappingDto.hasChanged(original: ExternalMovementSyncMappingDto) = this.prisonerNumber != original.prisonerNumber ||
  this.bookingId != original.bookingId ||
  this.nomisMovementSeq != original.nomisMovementSeq ||
  this.dpsMovementId != original.dpsMovementId ||
  this.nomisAddressId != original.nomisAddressId ||
  this.nomisAddressOwnerClass != original.nomisAddressOwnerClass ||
  this.dpsAddressText != original.dpsAddressText
