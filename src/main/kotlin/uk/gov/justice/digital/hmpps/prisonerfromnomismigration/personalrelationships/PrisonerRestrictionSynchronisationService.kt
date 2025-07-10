package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.TelemetryEnabled
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.doesOriginateInDps
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.telemetryOf
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.track
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.valuesAsStrings
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PrisonerRestrictionMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerRestriction
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
        val mapping = mappingApiService.getByNomisPrisonerRestrictionId(nomisRestrictionId)
        val nomisRestriction = nomisApiService.getPrisonerRestrictionById(nomisRestrictionId)
        dpsApiService.updatePrisonerRestriction(prisonerRestrictionId = mapping.dpsId.toLong(), nomisRestriction.toDpsSyncUpdatePrisonerRestrictionRequest())
      }
    }
  }
  suspend fun prisonerRestrictionDeleted(event: PrisonerRestrictionEvent) {
    val telemetry = telemetryOf(
      "offenderNo" to event.offenderIdDisplay,
      "nomisRestrictionId" to event.offenderRestrictionId,
    )
    track("contactperson-prisoner-restriction-synchronisation-deleted", telemetry) {
    }
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
