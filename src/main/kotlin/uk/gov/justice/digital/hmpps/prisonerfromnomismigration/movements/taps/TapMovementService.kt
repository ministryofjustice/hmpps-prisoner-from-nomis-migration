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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.Location
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.SyncAtAndBy
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.SyncWriteTapMovement
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.taps.ExternalMovementRetryMappingMessageTypes.RETRY_MAPPING_TAP_MOVEMENT
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.taps.ExternalMovementRetryMappingMessageTypes.RETRY_UPDATE_MAPPING_TAP_MOVEMENT
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.taps.MovementType.TAP
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TapMovementMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.TapMovementIn
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.TapMovementOut
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.InternalMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationType
import java.util.*

private const val TELEMETRY_PREFIX: String = "${TAP_TELEMETRY_PREFIX}-external-movement"

@Service
class TapMovementService(
  override val telemetryClient: TelemetryClient,
  private val queueService: SynchronisationQueueService,
  private val mappingApiService: TapMappingApiService,
  private val nomisApiService: TapsNomisApiService,
  private val dpsApiService: TapDpsApiService,
) : TelemetryEnabled {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun tapMovementChanged(event: ExternalMovementEvent) = when {
    event.movementType != TAP -> {}
    event.recordInserted -> tapMovementInserted(event)
    event.recordDeleted -> tapMovementDeleted(event)
    else -> tapMovementUpdated(event)
  }

  suspend fun tapMovementInserted(event: ExternalMovementEvent) {
    val (bookingId, prisonerNumber, movementSeq, _, directionCode) = event
    val telemetry = mutableMapOf<String, Any>(
      "offenderNo" to prisonerNumber!!,
      "bookingId" to bookingId,
      "movementSeq" to movementSeq,
      "directionCode" to directionCode,
    )

    if (event.originatesInDps) {
      telemetryClient.trackEvent("${TELEMETRY_PREFIX}-inserted-skipped", telemetry)
      return
    }

    mappingApiService.getTapMovementMappingOrNull(bookingId, movementSeq)
      ?.also {
        telemetry["dpsMovementId"] = it.dpsMovementId
        telemetryClient.trackEvent("${TELEMETRY_PREFIX}-inserted-ignored", telemetry)
      }
      ?: run {
        track("${TELEMETRY_PREFIX}-inserted", telemetry) {
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
    existingMapping: TapMovementMappingDto? = null,
  ): TapMovementMappingDto = nomisApiService.getTapMovementOut(prisonerNumber, bookingId, movementSeq)
    .also {
      it.tapScheduleOutId?.run { telemetry["nomisScheduledEventId"] = this }
      it.tapApplicationId?.run {
        telemetry["nomisApplicationId"] = this
        tryFetchParent { getParentApplicationId(this) }
          .also { telemetry["dpsAuthorisationId"] = it }
      }
    }
    .let { nomisMovement ->
      val dpsOccurrenceId =
        nomisMovement.tapScheduleOutId?.let { tryFetchParent { getParentScheduledId(it) } }
          ?.also { telemetry["dpsOccurrenceId"] = it }

      val dpsLocation = deriveDpsAddress(
        existingMapping,
        nomisMovement.toAddressId,
        nomisMovement.toAddressOwnerClass,
        nomisMovement.toFullAddress,
        nomisMovement.toAddressDescription,
        nomisMovement.toAddressPostcode,
      )
      dpsLocation.uprn?.also { telemetry["dpsUprn"] = it }
      val dpsMovement = nomisMovement.toDpsRequest(existingMapping?.dpsMovementId, dpsOccurrenceId, dpsLocation)
      val dpsMovementId = dpsApiService.syncTapMovement(prisonerNumber, dpsMovement).id
        .also { telemetry["dpsMovementId"] = it }

      TapMovementMappingDto(
        prisonerNumber = prisonerNumber,
        bookingId = bookingId,
        nomisMovementSeq = movementSeq,
        dpsMovementId = dpsMovementId,
        mappingType = TapMovementMappingDto.MappingType.NOMIS_CREATED,
        dpsAddressText = dpsLocation.address ?: "",
        dpsUprn = dpsLocation.uprn,
        dpsDescription = dpsLocation.description,
        dpsPostcode = dpsLocation.postcode,
        nomisAddressId = nomisMovement.toAddressId,
        nomisAddressOwnerClass = nomisMovement.toAddressOwnerClass,
      )
        .also {
          if (nomisMovement.toAddressId != null) telemetry["nomisAddressId"] = nomisMovement.toAddressId
          if (nomisMovement.toAddressOwnerClass != null) {
            telemetry["nomisAddressOwnerClass"] =
              nomisMovement.toAddressOwnerClass
          }
        }
    }

  private suspend fun syncExternalMovementTapIn(
    prisonerNumber: String,
    bookingId: Long,
    movementSeq: Int,
    telemetry: MutableMap<String, Any>,
    existingMapping: TapMovementMappingDto? = null,
  ): TapMovementMappingDto = nomisApiService.getTapMovementIn(prisonerNumber, bookingId, movementSeq)
    .also {
      it.tapScheduleOutId?.run { telemetry["nomisScheduledParentEventId"] = this }
      it.tapScheduleInId?.run { telemetry["nomisScheduledEventId"] = this }
      it.tapApplicationId?.run {
        telemetry["nomisApplicationId"] = this
        tryFetchParent { getParentApplicationId(this) }
          .also { telemetry["dpsAuthorisationId"] = it }
      }
    }
    .let { nomisMovement ->
      val dpsOccurrenceId =
        nomisMovement.tapScheduleOutId?.let { tryFetchParent { getParentScheduledId(it) } }
          ?.also { telemetry["dpsOccurrenceId"] = it }

      val dpsLocation = deriveDpsAddress(
        existingMapping,
        nomisMovement.fromAddressId,
        nomisMovement.fromAddressOwnerClass,
        nomisMovement.fromFullAddress,
        nomisMovement.fromAddressDescription,
        nomisMovement.fromAddressPostcode,
      )
      dpsLocation.uprn?.also { telemetry["dpsUprn"] = it }
      val dpsMovement = nomisMovement.toDpsRequest(existingMapping?.dpsMovementId, dpsOccurrenceId, dpsLocation)
      val dpsMovementId = dpsApiService.syncTapMovement(prisonerNumber, dpsMovement).id
        .also { telemetry["dpsMovementId"] = it }

      TapMovementMappingDto(
        prisonerNumber = prisonerNumber,
        bookingId = bookingId,
        nomisMovementSeq = movementSeq,
        dpsMovementId = dpsMovementId,
        mappingType = TapMovementMappingDto.MappingType.NOMIS_CREATED,
        dpsAddressText = dpsLocation.address ?: "",
        dpsUprn = dpsLocation.uprn,
        dpsDescription = dpsLocation.description,
        dpsPostcode = dpsLocation.postcode,
        nomisAddressId = nomisMovement.fromAddressId,
        nomisAddressOwnerClass = nomisMovement.fromAddressOwnerClass,
      )
        .also {
          if (nomisMovement.fromAddressId != null) telemetry["nomisAddressId"] = nomisMovement.fromAddressId
          if (nomisMovement.fromAddressOwnerClass != null) {
            telemetry["nomisAddressOwnerClass"] =
              nomisMovement.fromAddressOwnerClass
          }
        }
    }

  suspend fun tapMovementUpdated(event: ExternalMovementEvent) {
    val (bookingId, prisonerNumber, movementSeq, _, directionCode) = event
    val telemetry = mutableMapOf<String, Any>(
      "offenderNo" to prisonerNumber!!,
      "bookingId" to bookingId,
      "movementSeq" to movementSeq,
      "directionCode" to directionCode,
    )

    if (event.originatesInDps) {
      telemetryClient.trackEvent("${TELEMETRY_PREFIX}-updated-skipped", telemetry)
      return
    }

    track("${TELEMETRY_PREFIX}-updated", telemetry) {
      val existingMapping = mappingApiService.getTapMovementMappingOrNull(bookingId, movementSeq)
        ?.also { telemetry["dpsMovementId"] = it.dpsMovementId }
        ?: throw IllegalStateException("No mapping found when handling an update event for movement $bookingId/$movementSeq - hopefully messages are being processed out of order and this event will succeed on a retry once the create event is processed. Otherwise we need to understand why the original create event was never processed.")

      val newMapping = when (directionCode) {
        DirectionCode.OUT -> syncExternalMovementTapOut(
          prisonerNumber,
          bookingId,
          movementSeq,
          telemetry,
          existingMapping,
        )

        DirectionCode.IN -> syncExternalMovementTapIn(
          prisonerNumber,
          bookingId,
          movementSeq,
          telemetry,
          existingMapping,
        )
      }

      if (newMapping.hasChanged(existingMapping)) {
        tryToUpdateExternalMovementMapping(newMapping, telemetry)
      }
    }
  }

  suspend fun tapMovementDeleted(event: ExternalMovementEvent) {
    val (bookingId, prisonerNumber, movementSeq, _, directionCode) = event
    val telemetry = mutableMapOf<String, Any>(
      "offenderNo" to prisonerNumber!!,
      "bookingId" to bookingId,
      "movementSeq" to movementSeq,
      "directionCode" to directionCode,
    )
    mappingApiService.getTapMovementMappingOrNull(bookingId, movementSeq)?.also {
      track("${TELEMETRY_PREFIX}-deleted", telemetry) {
        telemetry["dpsMovementId"] = it.dpsMovementId
        dpsApiService.deleteTapMovement(it.dpsMovementId)
        mappingApiService.deleteTapMovementMapping(bookingId, movementSeq)
      }
    } ?: run { telemetryClient.trackEvent("${TELEMETRY_PREFIX}-deleted-ignored", telemetry) }
  }

  private suspend fun tryToCreateExternalMovementMapping(
    mapping: TapMovementMappingDto,
    telemetry: MutableMap<String, Any>,
  ) {
    try {
      mappingApiService.createTapMovementMapping(mapping).takeIf { it.isError }?.also {
        with(it.errorResponse!!.moreInfo) {
          telemetryClient.trackEvent(
            "${TELEMETRY_PREFIX}-inserted-duplicate",
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
      log.error(
        "Failed to create mapping for temporary absence external movement NOMIS id ${mapping.bookingId}/${mapping.nomisMovementSeq}",
        e,
      )
      queueService.sendMessage(
        messageType = RETRY_MAPPING_TAP_MOVEMENT.name,
        synchronisationType = SynchronisationType.EXTERNAL_MOVEMENTS,
        message = mapping,
        telemetryAttributes = telemetry.valuesAsStrings(),
      )
    }
  }

  suspend fun retryCreateExternalMovementMapping(retryMessage: InternalMessage<TapMovementMappingDto>) {
    mappingApiService.createTapMovementMapping(
      retryMessage.body,
    ).also {
      telemetryClient.trackEvent(
        "${TELEMETRY_PREFIX}-mapping-retry-created",
        retryMessage.telemetryAttributes,
      )
    }
  }

  suspend fun retryUpdateExternalMovementMapping(retryMessage: InternalMessage<TapMovementMappingDto>) {
    mappingApiService.updateTapMovementMapping(
      retryMessage.body,
    ).also {
      telemetryClient.trackEvent(
        "${TELEMETRY_PREFIX}-mapping-updated",
        retryMessage.telemetryAttributes,
      )
    }
  }

  private suspend fun deriveDpsAddress(
    existingMovementMapping: TapMovementMappingDto?,
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
      Location(
        existingMovementMapping.dpsDescription,
        existingMovementMapping.dpsAddressText,
        existingMovementMapping.dpsPostcode,
        existingMovementMapping.dpsUprn,
      )
    }
  }

  private suspend fun getParentApplicationId(nomisApplicationId: Long): UUID? = mappingApiService.getTapApplicationMappingOrNull(nomisApplicationId)
    ?.dpsAuthorisationId

  private suspend fun getParentScheduledId(nomisEventId: Long): UUID? = mappingApiService.getTapScheduleMappingOrNull(nomisEventId)
    ?.dpsOccurrenceId

  private suspend fun tryToUpdateExternalMovementMapping(
    mapping: TapMovementMappingDto,
    telemetry: MutableMap<String, Any>,
  ) {
    try {
      mappingApiService.updateTapMovementMapping(mapping)
    } catch (e: Exception) {
      log.error(
        "Failed to update mapping for temporary absence external movement NOMIS id ${mapping.bookingId}/${mapping.nomisMovementSeq}",
        e,
      )
      queueService.sendMessage(
        messageType = RETRY_UPDATE_MAPPING_TAP_MOVEMENT.name,
        synchronisationType = SynchronisationType.EXTERNAL_MOVEMENTS,
        message = mapping,
        telemetryAttributes = telemetry.valuesAsStrings(),
      )
    }
  }

  private fun TapMovementOut.toDpsRequest(
    id: UUID? = null,
    occurrenceId: UUID? = null,
    dpsLocation: Location,
  ) = SyncWriteTapMovement(
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

  private fun TapMovementIn.toDpsRequest(
    id: UUID? = null,
    occurrenceId: UUID? = null,
    dpsLocation: Location,
  ) = SyncWriteTapMovement(
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

  private fun TapMovementMappingDto.hasChanged(original: TapMovementMappingDto) = this.prisonerNumber != original.prisonerNumber ||
    this.bookingId != original.bookingId ||
    this.nomisMovementSeq != original.nomisMovementSeq ||
    this.dpsMovementId != original.dpsMovementId ||
    this.nomisAddressId != original.nomisAddressId ||
    this.nomisAddressOwnerClass != original.nomisAddressOwnerClass ||
    this.dpsAddressText != original.dpsAddressText
}
