package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.ParentEntityNotFoundRetry
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.TelemetryEnabled
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.doesOriginateInDps
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.track
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.valuesAsStrings
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.ExternalMovementRetryMappingMessageTypes.RETRY_MAPPING_TEMPORARY_ABSENCE_APPLICATION
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.ExternalMovementRetryMappingMessageTypes.RETRY_MAPPING_TEMPORARY_ABSENCE_EXTERNAL_MOVEMENT
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.ExternalMovementRetryMappingMessageTypes.RETRY_MAPPING_TEMPORARY_ABSENCE_OUTSIDE_MOVEMENT
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.ExternalMovementRetryMappingMessageTypes.RETRY_MAPPING_TEMPORARY_ABSENCE_SCHEDULED_MOVEMENT
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.MovementType.TAP
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.Address
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.ScheduledTemporaryAbsenceRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.TapApplicationRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.TapLocation
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.TapMovementRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.TapMovementRequest.Direction.IN
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.TapMovementRequest.Direction.OUT
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ExternalMovementSyncMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ScheduledMovementSyncMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TemporaryAbsenceApplicationSyncMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TemporaryAbsenceApplicationSyncMappingDto.MappingType.NOMIS_CREATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TemporaryAbsenceOutsideMovementSyncMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.NomisAudit
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.ScheduledTemporaryAbsenceResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.TemporaryAbsenceApplicationResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.TemporaryAbsenceResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.TemporaryAbsenceReturnResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.InternalMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationType
import java.util.*
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.NomisAudit as DpsAudit
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ScheduledMovementSyncMappingDto.MappingType.NOMIS_CREATED as SCHEDULED_MOVEMENT_NOMIS_CREATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TemporaryAbsenceOutsideMovementSyncMappingDto.MappingType.NOMIS_CREATED as OUTSIDE_MOVEMENT_NOMIS_CREATED

private const val TELEMETRY_PREFIX: String = "temporary-absence-sync"
private const val DEFAULT_ESCORT_CODE = "U"
private const val DEFAULT_TRANSPORT_TYPE = "TNR"

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

    if (event.doesOriginateInDps()) {
      telemetryClient.trackEvent("$TELEMETRY_PREFIX-application-inserted-skipped", telemetry)
      return
    }

    mappingApiService.getApplicationMapping(nomisApplicationId)
      ?.also { telemetryClient.trackEvent("$TELEMETRY_PREFIX-application-inserted-ignored", telemetry) }
      ?: run {
        track("$TELEMETRY_PREFIX-application-inserted", telemetry) {
          nomisApiService.getTemporaryAbsenceApplication(prisonerNumber, nomisApplicationId)
            .also {
              val dpsApplicationId = dpsApiService.syncTemporaryAbsenceApplication(prisonerNumber, it.toDpsRequest())
                .id
                .also { telemetry["dpsApplicationId"] = it }
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

    if (event.doesOriginateInDps()) {
      telemetryClient.trackEvent("$TELEMETRY_PREFIX-application-updated-skipped", telemetry)
      return
    }

    track("$TELEMETRY_PREFIX-application-updated", telemetry) {
      val dpsApplicationId = mappingApiService.getApplicationMapping(nomisApplicationId)!!.dpsMovementApplicationId
        .also { telemetry["dpsApplicationId"] = it }
      val nomisApplication = nomisApiService.getTemporaryAbsenceApplication(prisonerNumber, nomisApplicationId)
      dpsApiService.syncTemporaryAbsenceApplication(prisonerNumber, nomisApplication.toDpsRequest(dpsApplicationId))
    }
  }

  suspend fun movementApplicationDeleted(event: MovementApplicationEvent) {
    val (nomisApplicationId, bookingId, prisonerNumber) = event
    val telemetry = mutableMapOf<String, Any>(
      "offenderNo" to prisonerNumber,
      "bookingId" to bookingId,
      "nomisApplicationId" to nomisApplicationId,
    )
    mappingApiService.getApplicationMapping(nomisApplicationId)?.also {
      track("$TELEMETRY_PREFIX-application-deleted", telemetry) {
        telemetry["dpsApplicationId"] = it.dpsMovementApplicationId
        mappingApiService.deleteApplicationMapping(nomisApplicationId)
        // TODO delete in DPS
      }
    } ?: run { telemetryClient.trackEvent("$TELEMETRY_PREFIX-application-deleted-ignored", telemetry) }
  }

  suspend fun outsideMovementInserted(event: MovementApplicationMultiEvent) {
    val (nomisApplicationMultiId, nomisApplicationId, bookingId, prisonerNumber) = event
    val telemetry = mutableMapOf<String, Any>(
      "offenderNo" to prisonerNumber,
      "bookingId" to bookingId,
      "nomisApplicationMultiId" to nomisApplicationMultiId,
      "nomisApplicationId" to nomisApplicationId,
    )

    if (event.doesOriginateInDps()) {
      telemetryClient.trackEvent("$TELEMETRY_PREFIX-outside-movement-inserted-skipped", telemetry)
      return
    }

    mappingApiService.getOutsideMovementMapping(nomisApplicationMultiId)
      ?.also { telemetryClient.trackEvent("$TELEMETRY_PREFIX-outside-movement-inserted-ignored", telemetry) }
      ?: run {
        track("$TELEMETRY_PREFIX-outside-movement-inserted", telemetry) {
          requireParentApplicationExists(nomisApplicationId)
          nomisApiService.getTemporaryAbsenceApplicationOutsideMovement(prisonerNumber, nomisApplicationMultiId)
            .also {
              // TODO call DPS to synchronise outside movement
              val dpsOutsideMovementId = UUID.randomUUID().also { telemetry["dpsOutsideMovementId"] = it }
              val mapping = TemporaryAbsenceOutsideMovementSyncMappingDto(prisonerNumber, bookingId, nomisApplicationMultiId, dpsOutsideMovementId, OUTSIDE_MOVEMENT_NOMIS_CREATED)
              tryToCreateOutsideMovementMapping(mapping, telemetry)
            }
        }
      }
  }

  private suspend fun requireParentApplicationExists(nomisApplicationId: Long): UUID = getParentApplicationId(nomisApplicationId)
    ?: throw ParentEntityNotFoundRetry("Application $nomisApplicationId not created yet so children cannot be processed")

  private suspend fun getParentApplicationId(nomisApplicationId: Long): UUID? = mappingApiService.getApplicationMapping(nomisApplicationId)
    ?.dpsMovementApplicationId

  private suspend fun requireParentScheduleExists(nomisEventId: Long) = getParentScheduledId(nomisEventId)
    ?: throw ParentEntityNotFoundRetry("Scheduled event ID $nomisEventId not created yet so children cannot be processed")

  private suspend fun getParentScheduledId(nomisEventId: Long): UUID? = mappingApiService.getScheduledMovementMapping(nomisEventId)
    ?.dpsOccurrenceId

  suspend fun outsideMovementUpdated(event: MovementApplicationMultiEvent) {
    val (nomisApplicationMultiId, nomisApplicationId, bookingId, prisonerNumber) = event
    val telemetry = mutableMapOf<String, Any>(
      "offenderNo" to prisonerNumber,
      "bookingId" to bookingId,
      "nomisApplicationMultiId" to nomisApplicationMultiId,
      "nomisApplicationId" to nomisApplicationId,
    )

    if (event.doesOriginateInDps()) {
      telemetryClient.trackEvent("$TELEMETRY_PREFIX-outside-movement-updated-skipped", telemetry)
      return
    }

    track("$TELEMETRY_PREFIX-outside-movement-updated", telemetry) {
      val dpsOutsideMovementId = mappingApiService.getOutsideMovementMapping(nomisApplicationMultiId)!!.dpsOutsideMovementId
        .also { telemetry["dpsOutsideMovementId"] = it }
      val nomisOutsideMovement = nomisApiService.getTemporaryAbsenceApplicationOutsideMovement(prisonerNumber, nomisApplicationMultiId)
      // TODO update DPS
    }
  }

  suspend fun outsideMovementDeleted(event: MovementApplicationMultiEvent) {
    val (nomisApplicationMultiId, nomisApplicationId, bookingId, prisonerNumber) = event
    val telemetry = mutableMapOf<String, Any>(
      "offenderNo" to prisonerNumber,
      "bookingId" to bookingId,
      "nomisApplicationMultiId" to nomisApplicationMultiId,
      "nomisApplicationId" to nomisApplicationId,
    )
    mappingApiService.getOutsideMovementMapping(nomisApplicationMultiId)?.also {
      track("$TELEMETRY_PREFIX-outside-movement-deleted", telemetry) {
        telemetry["dpsOutsideMovementId"] = it.dpsOutsideMovementId
        mappingApiService.deleteOutsideMovementMapping(nomisApplicationMultiId)
        // TODO delete in DPS
      }
    } ?: run { telemetryClient.trackEvent("$TELEMETRY_PREFIX-outside-movement-deleted-ignored", telemetry) }
  }

  suspend fun scheduledMovementInserted(event: ScheduledMovementEvent) = when (event.eventMovementType) {
    TAP if (event.directionCode == DirectionCode.OUT) -> scheduledMovementTapOutInserted(event)
    else -> log.info("Ignoring insert of scheduled movement event ID ${event.eventId} with type ${event.eventMovementType} and direction ${event.directionCode} ")
  }

  suspend fun scheduledMovementTapOutInserted(event: ScheduledMovementEvent) {
    val (eventId, bookingId, prisonerNumber, _, directionCode) = event
    val telemetry = mutableMapOf<String, Any>(
      "offenderNo" to prisonerNumber,
      "bookingId" to bookingId,
      "nomisEventId" to eventId,
      "directionCode" to directionCode,
    )

    if (event.doesOriginateInDps()) {
      telemetryClient.trackEvent("$TELEMETRY_PREFIX-scheduled-movement-inserted-skipped", telemetry)
      return
    }

    mappingApiService.getScheduledMovementMapping(eventId)
      ?.also { telemetryClient.trackEvent("$TELEMETRY_PREFIX-scheduled-movement-inserted-ignored", telemetry) }
      ?: run {
        track("$TELEMETRY_PREFIX-scheduled-movement-inserted", telemetry) {
          val dpsScheduledMovementId = scheduledMovementTapOutInserted(prisonerNumber, eventId, telemetry)
            .also { telemetry["dpsScheduledMovementId"] = it }
          val mapping = ScheduledMovementSyncMappingDto(prisonerNumber, bookingId, eventId, dpsScheduledMovementId, SCHEDULED_MOVEMENT_NOMIS_CREATED, 0, "", "", "")
          tryToCreateScheduledMovementMapping(mapping, telemetry)
        }
      }
  }

  private suspend fun scheduledMovementTapOutInserted(prisonerNumber: String, eventId: Long, telemetry: MutableMap<String, Any>): UUID = nomisApiService.getTemporaryAbsenceScheduledMovement(prisonerNumber, eventId)
    .also {
      telemetry["nomisApplicationId"] = it.movementApplicationId
    }.let {
      val dpsApplicationId = requireParentApplicationExists(it.movementApplicationId)
      dpsApiService.syncTemporaryAbsenceScheduledMovement(dpsApplicationId, it.toDpsRequest()).id
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

    if (event.doesOriginateInDps()) {
      telemetryClient.trackEvent("$TELEMETRY_PREFIX-scheduled-movement-updated-skipped", telemetry)
      return
    }

    track("$TELEMETRY_PREFIX-scheduled-movement-updated", telemetry) {
      val dpsScheduledMovementId = mappingApiService.getScheduledMovementMapping(eventId)!!.dpsOccurrenceId
        .also { telemetry["dpsScheduledMovementId"] = it }
      scheduledMovementTapOutUpdated(dpsScheduledMovementId, prisonerNumber, eventId, telemetry)
    }
  }

  private suspend fun scheduledMovementTapOutUpdated(dpsScheduleMovementId: UUID, prisonerNumber: String, eventId: Long, telemetry: MutableMap<String, Any>) = nomisApiService.getTemporaryAbsenceScheduledMovement(prisonerNumber, eventId)
    .also { telemetry["nomisApplicationId"] = it.movementApplicationId }
    .let {
      val dpsApplicationId = requireParentApplicationExists(it.movementApplicationId)
      dpsApiService.syncTemporaryAbsenceScheduledMovement(dpsApplicationId, it.toDpsRequest(id = dpsScheduleMovementId)).id
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
    mappingApiService.getScheduledMovementMapping(eventId)?.also {
      track("$TELEMETRY_PREFIX-scheduled-movement-deleted", telemetry) {
        telemetry["dpsScheduledMovementId"] = it.dpsOccurrenceId
        mappingApiService.deleteScheduledMovementMapping(eventId)
        // TODO delete in DPS
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
              "existingOffenderNo" to existing.prisonerNumber,
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

  private suspend fun tryToCreateOutsideMovementMapping(mapping: TemporaryAbsenceOutsideMovementSyncMappingDto, telemetry: MutableMap<String, Any>) {
    try {
      mappingApiService.createOutsideMovementMapping(mapping).takeIf { it.isError }?.also {
        with(it.errorResponse!!.moreInfo) {
          telemetryClient.trackEvent(
            "$TELEMETRY_PREFIX-outside-movement-inserted-duplicate",
            mapOf(
              "existingOffenderNo" to existing.prisonerNumber,
              "existingBookingId" to existing.bookingId,
              "existingNomisApplicationMultiId" to existing.nomisMovementApplicationMultiId,
              "existingDpsOutsideMovementId" to existing.dpsOutsideMovementId,
              "duplicateOffenderNo" to duplicate.prisonerNumber,
              "duplicateBookingId" to duplicate.bookingId,
              "duplicateNomisApplicationMultiId" to duplicate.nomisMovementApplicationMultiId,
              "duplicateDpsOutsideMovementId" to duplicate.dpsOutsideMovementId,
            ),
          )
        }
      }
    } catch (e: Exception) {
      log.error("Failed to create mapping for temporary absence application multi NOMIS id ${mapping.nomisMovementApplicationMultiId}", e)
      queueService.sendMessage(
        messageType = RETRY_MAPPING_TEMPORARY_ABSENCE_OUTSIDE_MOVEMENT.name,
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
              "existingOffenderNo" to existing.prisonerNumber,
              "existingBookingId" to existing.bookingId,
              "existingNomisEventId" to existing.nomisEventId,
              "existingDpsScheduledMovementId" to existing.dpsOccurrenceId,
              "duplicateOffenderNo" to duplicate.prisonerNumber,
              "duplicateBookingId" to duplicate.bookingId,
              "duplicateNomisEventId" to duplicate.nomisEventId,
              "duplicateDpsScheduledMovementId" to duplicate.dpsOccurrenceId,
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

  suspend fun retryCreateApplicationMapping(retryMessage: InternalMessage<TemporaryAbsenceApplicationSyncMappingDto>) {
    mappingApiService.createApplicationMapping(
      retryMessage.body,
    ).also {
      telemetryClient.trackEvent(
        "$TELEMETRY_PREFIX-application-mapping-created",
        retryMessage.telemetryAttributes,
      )
    }
  }

  suspend fun retryCreateOutsideMovementMapping(retryMessage: InternalMessage<TemporaryAbsenceOutsideMovementSyncMappingDto>) {
    mappingApiService.createOutsideMovementMapping(
      retryMessage.body,
    ).also {
      telemetryClient.trackEvent(
        "$TELEMETRY_PREFIX-outside-movement-mapping-created",
        retryMessage.telemetryAttributes,
      )
    }
  }

  suspend fun retryCreateScheduledMovementMapping(retryMessage: InternalMessage<ScheduledMovementSyncMappingDto>) {
    mappingApiService.createScheduledMovementMapping(
      retryMessage.body,
    ).also {
      telemetryClient.trackEvent(
        "$TELEMETRY_PREFIX-scheduled-movement-mapping-created",
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
      "offenderNo" to prisonerNumber,
      "bookingId" to bookingId,
      "movementSeq" to movementSeq,
      "directionCode" to directionCode,
    )

    if (event.doesOriginateInDps()) {
      telemetryClient.trackEvent("$TELEMETRY_PREFIX-external-movement-inserted-skipped", telemetry)
      return
    }

    mappingApiService.getExternalMovementMapping(bookingId, movementSeq)
      ?.also {
        telemetry["dpsExternalMovementId"] = it.dpsMovementId
        telemetryClient.trackEvent("$TELEMETRY_PREFIX-external-movement-inserted-ignored", telemetry)
      }
      ?: run {
        track("$TELEMETRY_PREFIX-external-movement-inserted", telemetry) {
          val dpsExternalMovementId = when (directionCode) {
            DirectionCode.OUT -> externalMovementTapOutUpserted(prisonerNumber, bookingId, movementSeq, telemetry)
            DirectionCode.IN -> externalMovementTapInUpserted(prisonerNumber, bookingId, movementSeq, telemetry)
          }
            .also { telemetry["dpsExternalMovementId"] = it }
          tryToCreateExternalMovementMapping(
            ExternalMovementSyncMappingDto(prisonerNumber, bookingId, movementSeq, dpsExternalMovementId, ExternalMovementSyncMappingDto.MappingType.NOMIS_CREATED, "", 0, ""),
            telemetry,
          )
        }
      }
  }

  private suspend fun externalMovementTapOutUpserted(
    prisonerNumber: String,
    bookingId: Long,
    movementSeq: Int,
    telemetry: MutableMap<String, Any>,
    dpsExternalMovementId: UUID? = null,
  ) = nomisApiService.getTemporaryAbsenceMovement(prisonerNumber, bookingId, movementSeq)
    .also {
      it.scheduledTemporaryAbsenceId?.run { telemetry["nomisScheduledEventId"] = this }
      it.movementApplicationId?.run { telemetry["nomisApplicationId"] = this }
      it.movementApplicationId?.run { requireParentApplicationExists(it.movementApplicationId) }
    }
    .let {
      val occurrenceId = it.scheduledTemporaryAbsenceId?.let { requireParentScheduleExists(it) }
      dpsApiService.syncTemporaryAbsenceMovement(prisonerNumber, it.toDpsRequest(id = dpsExternalMovementId, occurrenceId = occurrenceId)).id
    }

  private suspend fun externalMovementTapInUpserted(
    prisonerNumber: String,
    bookingId: Long,
    movementSeq: Int,
    telemetry: MutableMap<String, Any>,
    dpsExternalMovementId: UUID? = null,
  ) = nomisApiService.getTemporaryAbsenceReturnMovement(prisonerNumber, bookingId, movementSeq)
    .also {
      it.scheduledTemporaryAbsenceId?.run { telemetry["nomisScheduledParentEventId"] = this }
      it.scheduledTemporaryAbsenceReturnId?.run { telemetry["nomisScheduledEventId"] = this }
      it.movementApplicationId?.run { telemetry["nomisApplicationId"] = this }
      it.movementApplicationId?.run { requireParentApplicationExists(it.movementApplicationId) }
    }
    .let {
      val occurrenceId = it.scheduledTemporaryAbsenceId?.let { requireParentScheduleExists(it) }
      dpsApiService.syncTemporaryAbsenceMovement(prisonerNumber, it.toDpsRequest(id = dpsExternalMovementId, occurrenceId = occurrenceId)).id
    }

  suspend fun externalMovementTapUpdated(event: ExternalMovementEvent) {
    val (bookingId, prisonerNumber, movementSeq, _, directionCode) = event
    val telemetry = mutableMapOf<String, Any>(
      "offenderNo" to prisonerNumber,
      "bookingId" to bookingId,
      "movementSeq" to movementSeq,
      "directionCode" to directionCode,
    )

    if (event.doesOriginateInDps()) {
      telemetryClient.trackEvent("$TELEMETRY_PREFIX-external-movement-updated-skipped", telemetry)
      return
    }

    track("$TELEMETRY_PREFIX-external-movement-updated", telemetry) {
      val dpsExternalMovementId = mappingApiService.getExternalMovementMapping(bookingId, movementSeq)!!.dpsMovementId
        .also { telemetry["dpsExternalMovementId"] = it }
      when (directionCode) {
        DirectionCode.OUT -> externalMovementTapOutUpserted(prisonerNumber, bookingId, movementSeq, telemetry, dpsExternalMovementId)
        DirectionCode.IN -> externalMovementTapInUpserted(prisonerNumber, bookingId, movementSeq, telemetry, dpsExternalMovementId)
      }
    }
  }

  suspend fun externalMovementTapDeleted(event: ExternalMovementEvent) {
    val (bookingId, prisonerNumber, movementSeq, _, directionCode) = event
    val telemetry = mutableMapOf<String, Any>(
      "offenderNo" to prisonerNumber,
      "bookingId" to bookingId,
      "movementSeq" to movementSeq,
      "directionCode" to directionCode,
    )
    mappingApiService.getExternalMovementMapping(bookingId, movementSeq)?.also {
      track("$TELEMETRY_PREFIX-external-movement-deleted", telemetry) {
        telemetry["dpsExternalMovementId"] = it.dpsMovementId
        mappingApiService.deleteExternalMovementMapping(bookingId, movementSeq)
        // TODO delete in DPS
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
              "existingOffenderNo" to existing.prisonerNumber,
              "existingBookingId" to existing.bookingId,
              "existingMovementSeq" to existing.nomisMovementSeq,
              "existingDpsExternalMovementId" to existing.dpsMovementId,
              "duplicateOffenderNo" to duplicate.prisonerNumber,
              "duplicateBookingId" to duplicate.bookingId,
              "duplicateMovementSeq" to duplicate.nomisMovementSeq,
              "duplicateDpsExternalMovementId" to duplicate.dpsMovementId,
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
        "$TELEMETRY_PREFIX-external-movement-mapping-created",
        retryMessage.telemetryAttributes,
      )
    }
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}

private fun TemporaryAbsenceApplicationResponse.toDpsRequest(id: UUID? = null) = TapApplicationRequest(
  id = id,
  movementApplicationId = movementApplicationId,
  eventSubType = eventSubType,
  applicationDate = applicationDate,
  fromDate = fromDate,
  toDate = toDate,
  applicationStatus = applicationStatus,
  applicationType = applicationType,
  prisonId = prisonId,
  comment = comment,
  contactPersonName = contactPersonName,
  temporaryAbsenceType = temporaryAbsenceType,
  temporaryAbsenceSubType = temporaryAbsenceSubType,
  audit = audit.toDpsRequest(),
)

private fun ScheduledTemporaryAbsenceResponse.toDpsRequest(id: UUID? = null) = ScheduledTemporaryAbsenceRequest(
  id = id,
  eventId = eventId,
  eventStatus = eventStatus,
  startTime = startTime,
  returnTime = returnTime,
//  toAddressOwnerClass = toAddressOwnerClass,
//  toAddressId = toAddressId,
  contactPersonName = contactPersonName,
  escort = escort ?: DEFAULT_ESCORT_CODE,
  transportType = transportType ?: DEFAULT_TRANSPORT_TYPE,
  comment = comment,
  location = TapLocation(
    id = toAddressId.toString(),
    typeCode = toAddressOwnerClass,
  ),
  audit = audit.toDpsRequest(),
)

private fun TemporaryAbsenceResponse.toDpsRequest(id: UUID? = null, occurrenceId: UUID? = null) = TapMovementRequest(
  id = id,
  occurrenceId = occurrenceId,
  legacyId = "${bookingId}_$sequence",
  movementDateTime = movementTime,
  movementReason = movementReason,
  direction = OUT,
  escort = escort,
  escortText = escortText,
  prisonCode = fromPrison,
  commentText = commentText,
  location = TapLocation(
    id = toAddressId.toString(),
    typeCode = toAddressOwnerClass,
    // TODO does full address go in description? Then why are we sending description back from nomis-prisoner-api?
    description = toAddressDescription,
    address = Address(
      postcode = toAddressPostcode,
    ),
  ),
  audit = audit.toDpsRequest(),
)

private fun TemporaryAbsenceReturnResponse.toDpsRequest(id: UUID? = null, occurrenceId: UUID? = null) = TapMovementRequest(
  id = id,
  occurrenceId = occurrenceId,
  legacyId = "${bookingId}_$sequence",
  movementDateTime = movementTime,
  movementReason = movementReason,
  direction = IN,
  escort = escort,
  escortText = escortText,
  prisonCode = toPrison,
  commentText = commentText,
  location = TapLocation(
    id = fromAddressId.toString(),
    typeCode = fromAddressOwnerClass,
    // TODO does full address go in description? Then why are we sending description back from nomis-prisoner-api?
    description = fromAddressDescription,
    address = Address(
      postcode = fromAddressPostcode,
    ),
  ),
  audit = audit.toDpsRequest(),
)

private fun NomisAudit.toDpsRequest() = DpsAudit(
  createDatetime = createDatetime,
  createUsername = createUsername,
  modifyDatetime = modifyDatetime,
  modifyUserId = modifyUserId,
  auditTimestamp = auditTimestamp,
  auditUserId = auditUserId,
)
