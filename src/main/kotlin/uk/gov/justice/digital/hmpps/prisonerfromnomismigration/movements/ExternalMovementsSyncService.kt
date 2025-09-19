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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.TapApplicationRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ExternalMovementSyncMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ScheduledMovementSyncMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TemporaryAbsenceApplicationSyncMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TemporaryAbsenceApplicationSyncMappingDto.MappingType.NOMIS_CREATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TemporaryAbsenceOutsideMovementSyncMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.NomisAudit
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.TemporaryAbsenceApplicationResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.InternalMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationType
import java.util.*
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.NomisAudit as DpsAudit
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ScheduledMovementSyncMappingDto.MappingType.NOMIS_CREATED as SCHEDULED_MOVEMENT_NOMIS_CREATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TemporaryAbsenceOutsideMovementSyncMappingDto.MappingType.NOMIS_CREATED as OUTSIDE_MOVEMENT_NOMIS_CREATED

private const val TELEMETRY_PREFIX: String = "temporary-absence-sync"

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

  private suspend fun requireParentApplicationExists(nomisApplicationId: Long) = mappingApiService.getApplicationMapping(nomisApplicationId)
    ?: throw ParentEntityNotFoundRetry("Application $nomisApplicationId not created yet so children cannot be processed")

  private suspend fun requireParentScheduleExists(nomisEventId: Long) = mappingApiService.getScheduledMovementMapping(nomisEventId)
    ?: throw ParentEntityNotFoundRetry("Scheduled event ID $nomisEventId not created yet so children cannot be processed")

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
    TAP -> scheduledMovementTapInserted(event)
    else -> log.info(("Ignoring scheduled movement event with type ${event.eventMovementType}"))
  }

  suspend fun scheduledMovementTapInserted(event: ScheduledMovementEvent) {
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
          val dpsScheduledMovementId = when (directionCode) {
            DirectionCode.OUT -> scheduledMovementTapOutInserted(prisonerNumber, eventId, telemetry)
            DirectionCode.IN -> scheduledMovementTapInInserted(prisonerNumber, eventId, telemetry)
          }.also {
            telemetry["dpsScheduledMovementId"] = it
          }
          val mapping = ScheduledMovementSyncMappingDto(prisonerNumber, bookingId, eventId, dpsScheduledMovementId, SCHEDULED_MOVEMENT_NOMIS_CREATED)
          tryToCreateScheduledMovementMapping(mapping, telemetry)
        }
      }
  }

  private suspend fun scheduledMovementTapOutInserted(prisonerNumber: String, eventId: Long, telemetry: MutableMap<String, Any>) = nomisApiService.getTemporaryAbsenceScheduledMovement(prisonerNumber, eventId)
    .also {
      telemetry["nomisApplicationId"] = it.movementApplicationId
      requireParentApplicationExists(it.movementApplicationId)
    }.let {
      // TODO dpsApi.sync(it.toDpsScheduledTemporaryAbsence()
      UUID.randomUUID()
    }

  private suspend fun scheduledMovementTapInInserted(prisonerNumber: String, eventId: Long, telemetry: MutableMap<String, Any>) = nomisApiService.getTemporaryAbsenceScheduledReturnMovement(prisonerNumber, eventId)
    .also {
      telemetry["nomisApplicationId"] = it.movementApplicationId
      requireParentApplicationExists(it.movementApplicationId)
    }.let {
      // TODO dpsApi.sync(it.toDpsScheduledTemporaryAbsenceReturn()
      UUID.randomUUID()
    }

  suspend fun scheduledMovementUpdated(event: ScheduledMovementEvent) = when (event.eventMovementType) {
    TAP -> scheduledMovementTapUpdated(event)
    else -> log.info(("Ignoring scheduled movement updated event with type ${event.eventMovementType}"))
  }

  suspend fun scheduledMovementTapUpdated(event: ScheduledMovementEvent) {
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
      val dpsScheduledMovementId = mappingApiService.getScheduledMovementMapping(eventId)!!.dpsScheduledMovementId
        .also { telemetry["dpsScheduledMovementId"] = it }
      when (directionCode) {
        DirectionCode.OUT -> scheduledMovementTapOutUpdated(dpsScheduledMovementId, prisonerNumber, eventId, telemetry)
        DirectionCode.IN -> scheduledMovementTapInUpdated(dpsScheduledMovementId, prisonerNumber, eventId, telemetry)
      }
    }
  }

  private suspend fun scheduledMovementTapOutUpdated(dpsScheduleMovementId: UUID, prisonerNumber: String, eventId: Long, telemetry: MutableMap<String, Any>) = nomisApiService.getTemporaryAbsenceScheduledMovement(prisonerNumber, eventId)
    .also { telemetry["nomisApplicationId"] = it.movementApplicationId }
    .let {
      // TODO dpsApi.sync(dpsScheduledMovementId, it.toDpsScheduledTemporaryAbsence()
    }

  private suspend fun scheduledMovementTapInUpdated(dpsScheduleMovementId: UUID, prisonerNumber: String, eventId: Long, telemetry: MutableMap<String, Any>) = nomisApiService.getTemporaryAbsenceScheduledReturnMovement(prisonerNumber, eventId)
    .also { telemetry["nomisApplicationId"] = it.movementApplicationId }
    .let {
      // TODO dpsApi.sync(dpsScheduledMovementId, it.toDpsScheduledTemporaryAbsenceReturn()
    }

  suspend fun scheduledMovementDeleted(event: ScheduledMovementEvent) = when (event.eventMovementType) {
    TAP -> scheduledMovementTapDeleted(event)
    else -> log.info(("Ignoring scheduled movement deleted event with type ${event.eventMovementType}"))
  }

  suspend fun scheduledMovementTapDeleted(event: ScheduledMovementEvent) {
    val (eventId, bookingId, prisonerNumber, _, directionCode) = event
    val telemetry = mutableMapOf<String, Any>(
      "offenderNo" to prisonerNumber,
      "bookingId" to bookingId,
      "nomisEventId" to eventId,
      "directionCode" to directionCode,
    )
    mappingApiService.getScheduledMovementMapping(eventId)?.also {
      track("$TELEMETRY_PREFIX-scheduled-movement-deleted", telemetry) {
        telemetry["dpsScheduledMovementId"] = it.dpsScheduledMovementId
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
              "existingDpsScheduledMovementId" to existing.dpsScheduledMovementId,
              "duplicateOffenderNo" to duplicate.prisonerNumber,
              "duplicateBookingId" to duplicate.bookingId,
              "duplicateNomisEventId" to duplicate.nomisEventId,
              "duplicateDpsScheduledMovementId" to duplicate.dpsScheduledMovementId,
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
      ?.also { telemetryClient.trackEvent("$TELEMETRY_PREFIX-external-movement-inserted-ignored", telemetry) }
      ?: run {
        track("$TELEMETRY_PREFIX-external-movement-inserted", telemetry) {
          val dpsExternalMovementId = when (directionCode) {
            DirectionCode.OUT -> externalMovementTapOutInserted(prisonerNumber, bookingId, movementSeq, telemetry)
            DirectionCode.IN -> externalMovementTapInInserted(prisonerNumber, bookingId, movementSeq, telemetry)
          }
            .also { telemetry["dpsExternalMovementId"] = it }
          tryToCreateExternalMovementMapping(
            ExternalMovementSyncMappingDto(prisonerNumber, bookingId, movementSeq, dpsExternalMovementId, ExternalMovementSyncMappingDto.MappingType.NOMIS_CREATED),
            telemetry,
          )
        }
      }
  }

  private suspend fun externalMovementTapOutInserted(prisonerNumber: String, bookingId: Long, movementSeq: Int, telemetry: MutableMap<String, Any>) = nomisApiService.getTemporaryAbsenceMovement(prisonerNumber, bookingId, movementSeq)
    .also {
      it.scheduledTemporaryAbsenceId?.run { telemetry["nomisScheduledEventId"] = this }
      it.movementApplicationId?.run { telemetry["nomisApplicationId"] = this }
      it.scheduledTemporaryAbsenceId?.run { requireParentScheduleExists(it.scheduledTemporaryAbsenceId) }
      it.movementApplicationId?.run { requireParentApplicationExists(it.movementApplicationId) }
    }
    .let {
      // TODO dpsApi.sync(it.toDpsTemporaryAbsence()) }
      UUID.randomUUID()
    }

  private suspend fun externalMovementTapInInserted(prisonerNumber: String, bookingId: Long, movementSeq: Int, telemetry: MutableMap<String, Any>) = nomisApiService.getTemporaryAbsenceReturnMovement(prisonerNumber, bookingId, movementSeq)
    .also {
      it.scheduledTemporaryAbsenceReturnId?.run { telemetry["nomisScheduledEventId"] = this }
      it.movementApplicationId?.run { telemetry["nomisApplicationId"] = this }
      it.scheduledTemporaryAbsenceReturnId?.run { requireParentScheduleExists(it.scheduledTemporaryAbsenceReturnId) }
      it.movementApplicationId?.run { requireParentApplicationExists(it.movementApplicationId) }
    }
    .let {
      // TODO dpsApi.sync(it.toDpsTemporaryAbsence()) }
      UUID.randomUUID()
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
      val dpsExternalMovementId = mappingApiService.getExternalMovementMapping(bookingId, movementSeq)!!.dpsExternalMovementId
        .also { telemetry["dpsExternalMovementId"] = it }
      when (directionCode) {
        DirectionCode.OUT -> externalMovementTapOutUpdated(dpsExternalMovementId, prisonerNumber, bookingId, movementSeq, telemetry)
        DirectionCode.IN -> externalMovementTapInUpdated(dpsExternalMovementId, prisonerNumber, bookingId, movementSeq, telemetry)
      }
    }
  }

  private suspend fun externalMovementTapOutUpdated(dpsExternalMovementId: UUID, prisonerNumber: String, bookingId: Long, movementSeq: Int, telemetry: MutableMap<String, Any>) = nomisApiService.getTemporaryAbsenceMovement(prisonerNumber, bookingId, movementSeq)
    .also {
      it.scheduledTemporaryAbsenceId?.run { telemetry["nomisScheduledEventId"] = this }
      it.movementApplicationId?.run { telemetry["nomisApplicationId"] = this }
    }
    .let {
      // TODO dpsApi.sync(dpsExternalMovementId, it.toDpsTemporaryAbsence()) }
    }

  private suspend fun externalMovementTapInUpdated(dpsExternalMovementId: UUID, prisonerNumber: String, bookingId: Long, movementSeq: Int, telemetry: MutableMap<String, Any>) = nomisApiService.getTemporaryAbsenceReturnMovement(prisonerNumber, bookingId, movementSeq)
    .also {
      it.scheduledTemporaryAbsenceReturnId?.run { telemetry["nomisScheduledEventId"] = this }
      it.movementApplicationId?.run { telemetry["nomisApplicationId"] = this }
    }
    .let {
      // TODO dpsApi.sync(dpsExternalMovementId, it.toDpsTemporaryAbsenceReturn()) }
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
        telemetry["dpsExternalMovementId"] = it.dpsExternalMovementId
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
              "existingDpsExternalMovementId" to existing.dpsExternalMovementId,
              "duplicateOffenderNo" to duplicate.prisonerNumber,
              "duplicateBookingId" to duplicate.bookingId,
              "duplicateMovementSeq" to duplicate.nomisMovementSeq,
              "duplicateDpsExternalMovementId" to duplicate.dpsExternalMovementId,
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
  releaseTime = releaseTime,
  toDate = toDate,
  returnTime = returnTime,
  applicationStatus = applicationStatus,
  applicationType = applicationType,
  transportType = transportType,
  escortCode = escortCode,
  prisonId = prisonId,
  toAgencyId = toAgencyId,
  toAddressId = toAddressId,
  toAddressOwnerClass = toAddressOwnerClass,
  comment = comment,
  contactPersonName = contactPersonName,
  temporaryAbsenceType = temporaryAbsenceType,
  temporaryAbsenceSubType = temporaryAbsenceSubType,
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
