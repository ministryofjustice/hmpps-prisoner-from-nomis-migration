package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nonassociations

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.SynchronisationMessageType.RETRY_SYNCHRONISATION_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.NonAssociationMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.NonAssociationMappingDto.MappingType.NOMIS_CREATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nonassociations.NonAssociationsSynchronisationService.MappingResponse.MAPPING_FAILED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.InternalMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationType.NON_ASSOCIATIONS
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.toUpsertSyncRequest

@Service
class NonAssociationsSynchronisationService(
  private val nomisApiService: NomisApiService,
  private val nonAssociationsMappingService: NonAssociationsMappingService,
  private val nonAssociationsService: NonAssociationsService,
  private val telemetryClient: TelemetryClient,
  private val queueService: SynchronisationQueueService,
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun synchroniseNonAssociationCreateOrUpdate(event: NonAssociationsOffenderEvent) {
    if (event.auditModuleName == "DPS_SYNCHRONISATION") {
      telemetryClient.trackEvent(
        "non-association-synchronisation-skipped",
        event.toTelemetryProperties(),
      )
      return
    }

    if (event.isNotPrimaryNonAssociation()) {
      telemetryClient.trackEvent(
        "non-association-synchronisation-non-primary-skipped",
        event.toTelemetryProperties(),
      )
      return
    }

    val nomisNonAssociation = nomisApiService.getNonAssociation(event.offenderIdDisplay, event.nsOffenderIdDisplay, event.typeSeq)
    nonAssociationsMappingService.findNomisNonAssociationMapping(
      firstOffenderNo = event.offenderIdDisplay,
      secondOffenderNo = event.nsOffenderIdDisplay,
      nomisTypeSequence = event.typeSeq,
    )?.let {
      log.debug("Found non-association mapping: $it")
      log.debug("Sending non-association upsert sync {}", nomisNonAssociation.toUpsertSyncRequest(it.nonAssociationId))

      nonAssociationsService.upsertNonAssociation(nomisNonAssociation.toUpsertSyncRequest(it.nonAssociationId))
      telemetryClient.trackEvent(
        "non-association-updated-synchronisation-success",
        event.toTelemetryProperties(it.nonAssociationId),
      )
    } ?: let {
      log.debug("No non-association mapping - sending non-association upsert sync {} ", nomisNonAssociation.toUpsertSyncRequest())

      nonAssociationsService.upsertNonAssociation(nomisNonAssociation.toUpsertSyncRequest()).also { nonAssociation ->
        tryToCreateNonAssociationMapping(event, nonAssociation.id).also { result ->
          telemetryClient.trackEvent(
            "non-association-created-synchronisation-success",
            event.toTelemetryProperties(
              nonAssociation.id,
              result == MAPPING_FAILED,
            ),
          )
        }
      }
    }
  }

  suspend fun synchroniseNonAssociationDelete(event: NonAssociationsOffenderEvent) {
    if (event.auditModuleName == "DPS_SYNCHRONISATION") {
      telemetryClient.trackEvent(
        "non-association-delete-synchronisation-skipped",
        event.toTelemetryProperties(),
      )
      return
    }

    if (event.isNotPrimaryNonAssociation()) {
      telemetryClient.trackEvent(
        "non-association-delete-synchronisation-non-primary-skipped",
        event.toTelemetryProperties(),
      )
      return
    }

    nonAssociationsMappingService.findNomisNonAssociationMapping(
      firstOffenderNo = event.offenderIdDisplay,
      secondOffenderNo = event.nsOffenderIdDisplay,
      nomisTypeSequence = event.typeSeq,
    )?.let {
      log.debug("Found non-association mapping: $it")
      log.debug("Sending non-association delete sync for {}", it.nonAssociationId)

      nonAssociationsService.deleteNonAssociation(it.nonAssociationId)

      nonAssociationsMappingService.deleteNomisNonAssociationMapping(it.nonAssociationId)

      telemetryClient.trackEvent(
        "non-association-delete-synchronisation-success",
        event.toTelemetryProperties(it.nonAssociationId),
      )
    } ?: let {
      log.debug(
        "No non-association mapping for " +
          "${event.offenderIdDisplay}, ${event.nsOffenderIdDisplay}, ${event.typeSeq} - ignored ",
      )

      telemetryClient.trackEvent(
        "non-association-delete-synchronisation-ignored",
        event.toTelemetryProperties(),
      )
    }
  }

  enum class MappingResponse {
    MAPPING_CREATED,
    MAPPING_FAILED,
  }

  suspend fun tryToCreateNonAssociationMapping(
    event: NonAssociationsOffenderEvent,
    nonAssociationId: Long,
  ): MappingResponse {
    val mapping = NonAssociationMappingDto(
      nonAssociationId = nonAssociationId,
      firstOffenderNo = event.offenderIdDisplay,
      secondOffenderNo = event.nsOffenderIdDisplay,
      nomisTypeSequence = event.typeSeq,
      mappingType = NOMIS_CREATED,
    )
    try {
      nonAssociationsMappingService.createMapping(
        mapping,
        object : ParameterizedTypeReference<DuplicateErrorResponse<NonAssociationMappingDto>>() {},
      ).also {
        if (it.isError) {
          val duplicateErrorDetails = (it.errorResponse!!).moreInfo
          telemetryClient.trackEvent(
            "from-nomis-synch-non-association-duplicate",
            mapOf<String, String>(
              "duplicateNonAssociationId" to duplicateErrorDetails.duplicate.nonAssociationId.toString(),
              "duplicateFirstOffenderNo" to duplicateErrorDetails.duplicate.firstOffenderNo,
              "duplicateSecondOffenderNo" to duplicateErrorDetails.duplicate.secondOffenderNo,
              "duplicateNomisTypeSequence" to duplicateErrorDetails.duplicate.nomisTypeSequence.toString(),
              "existingNonAssociationId" to duplicateErrorDetails.existing.nonAssociationId.toString(),
              "existingFirstOffenderNo" to duplicateErrorDetails.existing.firstOffenderNo,
              "existingSecondOffenderNo" to duplicateErrorDetails.existing.secondOffenderNo,
              "existingNomisTypeSequence" to duplicateErrorDetails.existing.nomisTypeSequence.toString(),
            ),
            null,
          )
        }
      }
      return MappingResponse.MAPPING_CREATED
    } catch (e: Exception) {
      log.error(
        "Failed to create mapping for nonAssociation id $nonAssociationId, firstOffenderNo ${event.offenderIdDisplay}, " +
          "secondOffenderNo ${event.nsOffenderIdDisplay}, typeSequence ${event.typeSeq}",
        e,
      )
      queueService.sendMessage(
        messageType = RETRY_SYNCHRONISATION_MAPPING.name,
        synchronisationType = NON_ASSOCIATIONS,
        message = mapping,
        telemetryAttributes = event.toTelemetryProperties(nonAssociationId),
      )
      return MAPPING_FAILED
    }
  }

  suspend fun retryCreateNonAssociationMapping(retryMessage: InternalMessage<NonAssociationMappingDto>) {
    nonAssociationsMappingService.createMapping(
      retryMessage.body,
      object : ParameterizedTypeReference<DuplicateErrorResponse<NonAssociationMappingDto>>() {},
    ).also {
      telemetryClient.trackEvent(
        "non-association-mapping-created-synchronisation-success",
        retryMessage.telemetryAttributes,
      )
    }
  }
}

// Two non-association events occur for each non-association relationship - use the offenderIds to uniquely identify each pair
private fun NonAssociationsOffenderEvent.isNotPrimaryNonAssociation(): Boolean = offenderIdDisplay > nsOffenderIdDisplay

private fun NonAssociationsOffenderEvent.toTelemetryProperties(
  nonAssociationId: Long? = null,
  mappingFailed: Boolean? = null,
) = mapOf(
  "firstOffenderNo" to this.offenderIdDisplay,
  "secondOffenderNo" to this.nsOffenderIdDisplay,
  "typeSequence" to this.typeSeq.toString(),
) + (nonAssociationId?.let { mapOf("nonAssociationId" to it.toString()) } ?: emptyMap()) + (
  mappingFailed?.takeIf { it }
    ?.let { mapOf("mapping" to "initial-failure") } ?: emptyMap()
  )
