package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.PrisonerBookingMovedDomainEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.PrisonerMergeDomainEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.PrisonerReceiveDomainEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.MoveBookingForPrisoner
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.TelemetryEnabled
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.WhichMoveBookingPrisoner
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.doesOriginateInDps
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.shouldReceiveEventHaveBeenRaisedAfterBookingMove
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.telemetryOf
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.track
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.valuesAsStrings
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ContactPersonSimpleMappingIdDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PrisonerRestrictionMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PrisonerRestrictionMappingsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.NomisAudit
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerRestriction
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.ContactPersonSynchronisationMessageType.RESYNCHRONISE_MOVE_BOOKING_PRISONER_RESTRICTION_TARGET
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.MergePrisonerRestrictionsRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.PrisonerRestrictionDetailsRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.ResetPrisonerRestrictionsRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncCreatePrisonerRestrictionRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncUpdatePrisonerRestrictionRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.InternalMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationType

@Service
class PrisonerRestrictionSynchronisationService(
  private val mappingApiService: PrisonerRestrictionMappingApiService,
  private val nomisApiService: ContactPersonNomisApiService,
  private val dpsApiService: ContactPersonDpsApiService,
  private val queueService: SynchronisationQueueService,
  override val telemetryClient: TelemetryClient,
) : TelemetryEnabled {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun prisonerRestrictionUpserted(event: PrisonerRestrictionEvent) = when (event.isUpdated) {
    true -> prisonerRestrictionUpdated(event)
    false -> prisonerRestrictionCreated(event)
  }
  suspend fun prisonerRestrictionCreated(event: PrisonerRestrictionEvent) {
    val nomisRestrictionId = event.offenderRestrictionId
    val offenderNo = event.offenderIdDisplay
    val telemetry = telemetryOf(
      "offenderNo" to offenderNo,
      "nomisRestrictionId" to nomisRestrictionId,
    )

    if (event.doesOriginateInDps()) {
      telemetryClient.trackEvent(
        "contactperson-prisoner-restriction-synchronisation-created-skipped",
        telemetry,
      )
    } else {
      val mapping = mappingApiService.getByNomisPrisonerRestrictionIdOrNull(nomisRestrictionId)
      if (mapping != null) {
        telemetryClient.trackEvent(
          "contactperson-prisoner-restriction-synchronisation-created-ignored",
          telemetry + ("dpsRestrictionId" to mapping.dpsId),
        )
      } else {
        track("contactperson-prisoner-restriction-synchronisation-created", telemetry) {
          val nomisRestriction = nomisApiService.getPrisonerRestrictionById(nomisRestrictionId)
          val dpsRestriction = dpsApiService.createPrisonerRestriction(nomisRestriction.toDpsSyncCreatePrisonerRestrictionRequest())
          tryToCreateMapping(
            mapping = PrisonerRestrictionMappingDto(
              dpsId = dpsRestriction.prisonerRestrictionId.toString(),
              nomisId = nomisRestrictionId,
              offenderNo = offenderNo,
              mappingType = PrisonerRestrictionMappingDto.MappingType.NOMIS_CREATED,
            ),
            telemetry = telemetry,
          )
        }
      }
    }
  }
  suspend fun prisonerRestrictionUpdated(event: PrisonerRestrictionEvent) {
    val nomisRestrictionId = event.offenderRestrictionId
    val offenderNo = event.offenderIdDisplay
    val telemetry = telemetryOf(
      "offenderNo" to offenderNo,
      "nomisRestrictionId" to nomisRestrictionId,
    )
    if (event.doesOriginateInDps()) {
      telemetryClient.trackEvent(
        "contactperson-prisoner-restriction-synchronisation-updated-skipped",
        telemetry,
      )
    } else {
      track("contactperson-prisoner-restriction-synchronisation-updated", telemetry) {
        val mapping = mappingApiService.getByNomisPrisonerRestrictionId(nomisRestrictionId).also { telemetry["dpsRestrictionId"] = it.dpsId }
        val nomisRestriction = nomisApiService.getPrisonerRestrictionById(nomisRestrictionId)
        dpsApiService.updatePrisonerRestriction(prisonerRestrictionId = mapping.dpsId.toLong(), nomisRestriction.toDpsSyncUpdatePrisonerRestrictionRequest())
      }
    }
  }
  suspend fun prisonerRestrictionDeleted(event: PrisonerRestrictionEvent) {
    val nomisRestrictionId = event.offenderRestrictionId
    val offenderNo = event.offenderIdDisplay
    val telemetry = telemetryOf(
      "offenderNo" to offenderNo,
      "nomisRestrictionId" to nomisRestrictionId,
    )

    val mapping = mappingApiService.getByNomisPrisonerRestrictionIdOrNull(nomisRestrictionId)
    if (mapping == null) {
      telemetryClient.trackEvent(
        "contactperson-prisoner-restriction-synchronisation-deleted-ignored",
        telemetry,
      )
    } else {
      telemetry["dpsRestrictionId"] = mapping.dpsId
      track("contactperson-prisoner-restriction-synchronisation-deleted", telemetry) {
        dpsApiService.deletePrisonerRestriction(prisonerRestrictionId = mapping.dpsId.toLong())
        mappingApiService.deleteByNomisPrisonerRestrictionId(nomisRestrictionId)
      }
    }
  }

  suspend fun prisonerMerged(prisonerMergeEvent: PrisonerMergeDomainEvent) {
    val retainedOffenderNumber = prisonerMergeEvent.additionalInformation.nomsNumber
    val removedOffenderNumber = prisonerMergeEvent.additionalInformation.removedNomsNumber
    val telemetry = telemetryOf(
      "offenderNo" to retainedOffenderNumber,
      "bookingId" to prisonerMergeEvent.additionalInformation.bookingId,
      "removedOffenderNo" to removedOffenderNumber,
    )

    track("from-nomis-synch-prisonerrestriction-merge", telemetry) {
      val nomisRestrictions = nomisApiService.getPrisonerRestrictions(retainedOffenderNumber).restrictions.also {
        telemetry["restrictionsCount"] = it.size
      }

      val dpsChangedResponse = dpsApiService.mergePrisonerRestrictions(
        MergePrisonerRestrictionsRequest(
          restrictions = nomisRestrictions.map { it.toDpsSyncPrisonerRestriction() },
          keepingPrisonerNumber = retainedOffenderNumber,
          removingPrisonerNumber = removedOffenderNumber,
        ),
      )

      val mappings = dpsChangedResponse.createdRestrictions.zip(nomisRestrictions) { dpsRestriction, nomisRestriction ->
        ContactPersonSimpleMappingIdDto(
          dpsId = dpsRestriction.toString(),
          nomisId = nomisRestriction.id,
        )
      }
      mappingApiService.replaceAfterMerge(
        retainedOffenderNo = retainedOffenderNumber,
        removedOffenderNo = removedOffenderNumber,
        PrisonerRestrictionMappingsDto(
          mappingType = PrisonerRestrictionMappingsDto.MappingType.NOMIS_CREATED,
          mappings = mappings,
        ),
      )
    }
  }
  suspend fun prisonerBookingMoved(prisonerBookingMovedEvent: PrisonerBookingMovedDomainEvent) {
    val bookingId = prisonerBookingMovedEvent.additionalInformation.bookingId
    val movedToNomsNumber = prisonerBookingMovedEvent.additionalInformation.movedToNomsNumber
    val movedFromNomsNumber = prisonerBookingMovedEvent.additionalInformation.movedFromNomsNumber

    resetAfterPrisonerBookingMoved(
      MoveBookingForPrisoner(
        bookingId = bookingId,
        offenderNo = movedFromNomsNumber,
        whichPrisoner = WhichMoveBookingPrisoner.FROM,
      ),
    )
    // the target prisoner may need reset as well, depending on if the reset hasn't already been
    // done by the receive event - in which case it will be skipped.
    // send message to guarantee this eventually processed
    queueService.sendMessage(
      messageType = RESYNCHRONISE_MOVE_BOOKING_PRISONER_RESTRICTION_TARGET.name,
      synchronisationType = SynchronisationType.PERSONALRELATIONSHIPS,
      message = MoveBookingForPrisoner(
        bookingId = bookingId,
        offenderNo = movedToNomsNumber,
        whichPrisoner = WhichMoveBookingPrisoner.TO,
      ),
    )
  }

  private suspend fun resetAfterPrisonerBookingMoved(moveBooking: MoveBookingForPrisoner) {
    val bookingId = moveBooking.bookingId
    val offenderNo = moveBooking.offenderNo

    val telemetry = mutableMapOf<String, Any>(
      "bookingId" to bookingId,
      "whichPrisoner" to moveBooking.whichPrisoner.name,
      "offenderNo" to offenderNo,
    )

    resetRestrictions(offenderNo, telemetry)

    telemetryClient.trackEvent(
      "from-nomis-synch-prisonerrestriction-booking-moved-success",
      telemetry,
    )
  }

  suspend fun resetAfterPrisonerBookingMovedIfNecessary(movePrisonerMessage: InternalMessage<MoveBookingForPrisoner>) {
    val movePrisoner = movePrisonerMessage.body
    val bookingId = movePrisoner.bookingId
    val offenderNo = movePrisoner.offenderNo

    if (nomisApiService.getPrisonerDetails(movePrisonerMessage.body.offenderNo).shouldReceiveEventHaveBeenRaisedAfterBookingMove()) {
      telemetryClient.trackEvent(
        "from-nomis-synch-prisonerrestriction-booking-moved-ignored",
        mapOf(
          "bookingId" to bookingId,
          "whichPrisoner" to movePrisoner.whichPrisoner.name,
          "offenderNo" to offenderNo,
        ),
      )
    } else {
      resetAfterPrisonerBookingMoved(
        MoveBookingForPrisoner(
          bookingId = bookingId,
          offenderNo = offenderNo,
          whichPrisoner = WhichMoveBookingPrisoner.TO,
        ),
      )
    }
  }

  suspend fun resetPrisonerContactsForAdmission(prisonerReceivedEvent: PrisonerReceiveDomainEvent) {
    val offenderNo = prisonerReceivedEvent.additionalInformation.nomsNumber
    val telemetry = telemetryOf(
      "offenderNo" to offenderNo,
    )

    when (prisonerReceivedEvent.additionalInformation.reason) {
      "READMISSION_SWITCH_BOOKING", "NEW_ADMISSION" -> {
        resetRestrictions(offenderNo, telemetry)
        telemetryClient.trackEvent(
          "from-nomis-synch-prisonerrestriction-booking-changed-success",
          telemetry,
        )
      }
      else -> {
        telemetryClient.trackEvent(
          "from-nomis-synch-prisonerrestriction-booking-changed-ignored",
          telemetry,
        )
      }
    }
  }

  private suspend fun resetRestrictions(offenderNo: String, telemetry: MutableMap<String, Any>) {
    val nomisRestrictions = nomisApiService.getPrisonerRestrictions(offenderNo).restrictions.also {
      telemetry["restrictionsCount"] = it.size
    }
    val dpsChangedResponse = dpsApiService.resetPrisonerRestrictions(
      ResetPrisonerRestrictionsRequest(
        restrictions = nomisRestrictions.map { it.toDpsSyncPrisonerRestriction() },
        prisonerNumber = offenderNo,
      ),
    )

    val mappings = dpsChangedResponse.createdRestrictions.zip(nomisRestrictions) { dpsRestriction, nomisRestriction ->
      ContactPersonSimpleMappingIdDto(
        dpsId = dpsRestriction.toString(),
        nomisId = nomisRestriction.id,
      )
    }
    mappingApiService.replace(
      offenderNo = offenderNo,
      PrisonerRestrictionMappingsDto(
        mappingType = PrisonerRestrictionMappingsDto.MappingType.NOMIS_CREATED,
        mappings = mappings,
      ),
    )
  }

  private suspend fun tryToCreateMapping(
    mapping: PrisonerRestrictionMappingDto,
    telemetry: Map<String, Any>,
  ) {
    try {
      createMapping(mapping)
    } catch (e: Exception) {
      log.error("Failed to create mapping for prisoner restriction id ${mapping.nomisId}", e)
      queueService.sendMessage(
        messageType = ContactPersonSynchronisationMessageType.RETRY_SYNCHRONISATION_PRISONER_RESTRICTION_MAPPING.name,
        synchronisationType = SynchronisationType.PERSONALRELATIONSHIPS,
        message = mapping,
        telemetryAttributes = telemetry.valuesAsStrings(),
      )
    }
  }
  private suspend fun createMapping(
    mapping: PrisonerRestrictionMappingDto,
  ) {
    mappingApiService.createMapping(mapping, errorJavaClass = object : ParameterizedTypeReference<DuplicateErrorResponse<PrisonerRestrictionMappingDto>>() {}).takeIf { it.isError }?.also {
      with(it.errorResponse!!.moreInfo) {
        telemetryClient.trackEvent(
          "from-nomis-sync-contactperson-duplicate",
          mapOf(
            "existingNomisId" to existing.nomisId,
            "existingDpsId" to existing.dpsId,
            "duplicateNomisId" to duplicate.nomisId,
            "duplicateDpsId" to duplicate.dpsId,
            "type" to "PRISONER_RESTRICTION",
          ),
        )
      }
    }
  }

  suspend fun retryCreatePrisonerRestrictionMapping(retryMessage: InternalMessage<PrisonerRestrictionMappingDto>) {
    createMapping(retryMessage.body)
      .also {
        telemetryClient.trackEvent(
          "contactperson-prisoner-restriction-mapping-synchronisation-created",
          retryMessage.telemetryAttributes,
        )
      }
  }
}

fun PrisonerRestriction.toDpsSyncCreatePrisonerRestrictionRequest() = SyncCreatePrisonerRestrictionRequest(
  restrictionType = this.type.code,
  effectiveDate = this.effectiveDate,
  authorisedUsername = this.authorisedStaff.username,
  currentTerm = this.bookingSequence == 1L,
  expiryDate = this.expiryDate,
  commentText = this.comment,
  createdTime = this.audit.createDatetime,
  createdBy = this.enteredStaff.username,
  prisonerNumber = this.offenderNo,
)
fun PrisonerRestriction.toDpsSyncUpdatePrisonerRestrictionRequest() = SyncUpdatePrisonerRestrictionRequest(
  restrictionType = this.type.code,
  effectiveDate = this.effectiveDate,
  authorisedUsername = this.authorisedStaff.username,
  currentTerm = this.bookingSequence == 1L,
  expiryDate = this.expiryDate,
  commentText = this.comment,
  prisonerNumber = this.offenderNo,
  updatedTime = this.audit.modifyDatetime,
  updatedBy = this.enteredStaff.username,
)

fun PrisonerRestriction.toDpsSyncPrisonerRestriction() = PrisonerRestrictionDetailsRequest(
  restrictionType = type.code,
  effectiveDate = effectiveDate,
  expiryDate = expiryDate,
  commentText = comment,
  authorisedUsername = authorisedStaff.username,
  currentTerm = bookingSequence == 1L,
  createdTime = audit.createDatetime,
  createdBy = if (audit.hasBeenModified()) {
    audit.createUsername
  } else {
    enteredStaff.username
  },
  updatedTime = audit.modifyDatetime,
  updatedBy = if (audit.hasBeenModified()) {
    enteredStaff.username
  } else {
    audit.modifyUserId
  },
)

private fun NomisAudit.hasBeenModified() = this.modifyUserId != null
