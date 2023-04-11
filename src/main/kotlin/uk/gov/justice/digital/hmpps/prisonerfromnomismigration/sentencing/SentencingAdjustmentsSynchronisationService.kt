package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.SynchronisationMessageType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing.SentencingAdjustmentsSynchronisationService.MappingResponse.MAPPING_CREATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing.SentencingAdjustmentsSynchronisationService.MappingResponse.MAPPING_FAILED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.InternalMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationType

@Service
class SentencingAdjustmentsSynchronisationService(
  private val sentencingAdjustmentsMappingService: SentencingAdjustmentsMappingService,
  private val nomisApiService: NomisApiService,
  private val sentencingService: SentencingService,
  private val telemetryClient: TelemetryClient,
  private val queueService: SynchronisationQueueService,
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun synchroniseSentenceAdjustmentCreateOrUpdate(event: SentenceAdjustmentOffenderEvent) {
    if (event.auditModuleName == "DPS_SYNCHRONISATION") {
      telemetryClient.trackEvent(
        "sentence-adjustment-synchronisation-skipped",
        event.toTelemetryProperties(),
      )
      return
    }
    nomisApiService.getSentenceAdjustment(event.adjustmentId).takeUnless { it.hiddenFromUsers }
      ?.also { nomisAdjustment ->
        sentencingAdjustmentsMappingService.findNomisSentencingAdjustmentMapping(
          nomisAdjustmentId = event.adjustmentId,
          nomisAdjustmentCategory = "SENTENCE",
        )?.let {
          sentencingService.updateSentencingAdjustment(it.adjustmentId, nomisAdjustment.toSentencingAdjustment())
          telemetryClient.trackEvent(
            "sentence-adjustment-updated-synchronisation-success",
            event.toTelemetryProperties(it.adjustmentId),
          )
        } ?: let {
          sentencingService.createSentencingAdjustment(nomisAdjustment.toSentencingAdjustment()).also { adjustment ->
            tryToCreateSentenceMapping(event, adjustment.adjustmentId).also { result ->
              telemetryClient.trackEvent(
                "sentence-adjustment-created-synchronisation-success",
                event.toTelemetryProperties(adjustment.adjustmentId, result == MAPPING_FAILED),
              )
            }
          }
        }
      } ?: also {
      telemetryClient.trackEvent(
        "sentence-adjustment-hidden-synchronisation-skipped",
        event.toTelemetryProperties(),
      )
    }
  }

  suspend fun synchroniseSentenceAdjustmentDelete(event: SentenceAdjustmentOffenderEvent) {
    sentencingAdjustmentsMappingService.findNomisSentencingAdjustmentMapping(
      nomisAdjustmentId = event.adjustmentId,
      nomisAdjustmentCategory = "SENTENCE",
    )?.let {
      sentencingService.deleteSentencingAdjustment(it.adjustmentId)
      sentencingAdjustmentsMappingService.deleteNomisSentenceAdjustmentMapping(it.adjustmentId)
      telemetryClient.trackEvent(
        "sentence-adjustment-delete-synchronisation-success",
        event.toTelemetryProperties(adjustmentId = it.adjustmentId),
      )
    } ?: let {
      telemetryClient.trackEvent(
        "sentence-adjustment-delete-synchronisation-ignored",
        event.toTelemetryProperties(),
      )
    }
  }

  suspend fun synchroniseKeyDateAdjustmentCreateOrUpdate(event: KeyDateAdjustmentOffenderEvent) {
    if (event.auditModuleName == "DPS_SYNCHRONISATION") {
      telemetryClient.trackEvent(
        "key-date-adjustment-synchronisation-skipped",
        event.toTelemetryProperties(),
      )
      return
    }
    val nomisAdjustment = nomisApiService.getKeyDateAdjustment(event.adjustmentId)
    sentencingAdjustmentsMappingService.findNomisSentencingAdjustmentMapping(
      nomisAdjustmentId = event.adjustmentId,
      nomisAdjustmentCategory = "KEY-DATE",
    )?.let {
      sentencingService.updateSentencingAdjustment(it.adjustmentId, nomisAdjustment.toSentencingAdjustment())
      telemetryClient.trackEvent(
        "key-date-adjustment-updated-synchronisation-success",
        event.toTelemetryProperties(it.adjustmentId),
      )
    } ?: let {
      sentencingService.createSentencingAdjustment(nomisAdjustment.toSentencingAdjustment()).also { adjustment ->
        tryToCreateKeyDateMapping(event, adjustment.adjustmentId).also { result ->
          telemetryClient.trackEvent(
            "key-date-adjustment-created-synchronisation-success",
            event.toTelemetryProperties(adjustment.adjustmentId, result == MAPPING_FAILED),
          )
        }
      }
    }
  }

  suspend fun synchroniseKeyDateAdjustmentDelete(event: KeyDateAdjustmentOffenderEvent) {
    sentencingAdjustmentsMappingService.findNomisSentencingAdjustmentMapping(
      nomisAdjustmentId = event.adjustmentId,
      nomisAdjustmentCategory = "KEY-DATE",
    )?.let {
      sentencingService.deleteSentencingAdjustment(it.adjustmentId)
      sentencingAdjustmentsMappingService.deleteNomisSentenceAdjustmentMapping(it.adjustmentId)
      telemetryClient.trackEvent(
        "key-date-adjustment-delete-synchronisation-success",
        event.toTelemetryProperties(adjustmentId = it.adjustmentId),
      )
    } ?: let {
      telemetryClient.trackEvent(
        "key-date-adjustment-delete-synchronisation-ignored",
        event.toTelemetryProperties(),
      )
    }
  }

  enum class MappingResponse {
    MAPPING_CREATED,
    MAPPING_FAILED,
  }

  suspend fun tryToCreateSentenceMapping(
    event: SentenceAdjustmentOffenderEvent,
    adjustmentId: String,
  ): MappingResponse {
    val mapping = SentencingAdjustmentNomisMapping(
      nomisAdjustmentId = event.adjustmentId,
      nomisAdjustmentCategory = "SENTENCE",
      adjustmentId = adjustmentId,
      mappingType = "NOMIS_CREATED",
    )
    try {
      sentencingAdjustmentsMappingService.createMapping(
        mapping,
        object : ParameterizedTypeReference<DuplicateErrorResponse<SentencingAdjustmentNomisMapping>>() {},
      ).also {
        if (it.isError) {
          val duplicateErrorDetails = (it.errorResponse!!).moreInfo
          telemetryClient.trackEvent(
            "from-nomis-synch-adjustment-duplicate",
            mapOf<String, String>(
              "duplicateAdjustmentId" to duplicateErrorDetails.duplicate.adjustmentId,
              "duplicateNomisAdjustmentId" to duplicateErrorDetails.duplicate.nomisAdjustmentId.toString(),
              "duplicateNomisAdjustmentCategory" to duplicateErrorDetails.duplicate.nomisAdjustmentCategory,
              "existingAdjustmentId" to duplicateErrorDetails.existing.adjustmentId,
              "existingNomisAdjustmentId" to duplicateErrorDetails.existing.nomisAdjustmentId.toString(),
              "existingNomisAdjustmentCategory" to duplicateErrorDetails.existing.nomisAdjustmentCategory,
            ),
            null,
          )
        }
      }
      return MAPPING_CREATED
    } catch (e: Exception) {
      log.error(
        "Failed to create mapping for adjustment id $adjustmentId, nomisAdjustmentId ${event.adjustmentId}, nomisAdjustmentCategory SENTENCE",
        e,
      )
      queueService.sendMessage(
        messageType = SynchronisationMessageType.RETRY_SYNCHRONISATION_MAPPING.name,
        synchronisationType = SynchronisationType.SENTENCING_ADJUSTMENTS,
        message = mapping,
        telemetryAttributes = event.toTelemetryProperties(adjustmentId),
      )
      return MAPPING_FAILED
    }
  }

  suspend fun tryToCreateKeyDateMapping(
    event: KeyDateAdjustmentOffenderEvent,
    adjustmentId: String,
  ): MappingResponse {
    val mapping = SentencingAdjustmentNomisMapping(
      nomisAdjustmentId = event.adjustmentId,
      nomisAdjustmentCategory = "KEY-DATE",
      adjustmentId = adjustmentId,
      mappingType = "NOMIS_CREATED",
    )
    try {
      sentencingAdjustmentsMappingService.createMapping(
        mapping,
        object : ParameterizedTypeReference<DuplicateErrorResponse<SentencingAdjustmentNomisMapping>>() {},
      )
      return MAPPING_CREATED
    } catch (e: Exception) {
      log.error(
        "Failed to create mapping for adjustment id $adjustmentId, nomisAdjustmentId ${event.adjustmentId}, nomisAdjustmentCategory KEY-DATE",
        e,
      )
      queueService.sendMessage(
        messageType = SynchronisationMessageType.RETRY_SYNCHRONISATION_MAPPING.name,
        synchronisationType = SynchronisationType.SENTENCING_ADJUSTMENTS,
        message = mapping,
        telemetryAttributes = event.toTelemetryProperties(adjustmentId),
      )
      return MAPPING_FAILED
    }
  }

  suspend fun retryCreateSentenceAdjustmentMapping(retryMessage: InternalMessage<SentencingAdjustmentNomisMapping>) {
    sentencingAdjustmentsMappingService.createMapping(
      retryMessage.body,
      object : ParameterizedTypeReference<DuplicateErrorResponse<SentencingAdjustmentNomisMapping>>() {},
    ).also {
      telemetryClient.trackEvent(
        "adjustment-mapping-created-synchronisation-success",
        retryMessage.telemetryAttributes,
      )
    }
  }
}

private fun SentenceAdjustmentOffenderEvent.toTelemetryProperties(
  adjustmentId: String? = null,
  mappingFailed: Boolean? = null,
) = mapOf(
  "offenderNo" to this.offenderIdDisplay,
  "bookingId" to this.bookingId.toString(),
  "sentenceSequence" to this.sentenceSeq.toString(),
  "nomisAdjustmentId" to this.adjustmentId.toString(),
  "adjustmentCategory" to "SENTENCE",
) + (adjustmentId?.let { mapOf("adjustmentId" to it) } ?: emptyMap()) + (
  mappingFailed?.takeIf { it }
    ?.let { mapOf("mapping" to "initial-failure") } ?: emptyMap()
  )

private fun KeyDateAdjustmentOffenderEvent.toTelemetryProperties(
  adjustmentId: String? = null,
  mappingFailed: Boolean? = null,
) = mapOf(
  "offenderNo" to this.offenderIdDisplay,
  "bookingId" to this.bookingId.toString(),
  "nomisAdjustmentId" to this.adjustmentId.toString(),
  "adjustmentCategory" to "KEY-DATE",
) + (adjustmentId?.let { mapOf("adjustmentId" to it) } ?: emptyMap()) + (
  mappingFailed?.takeIf { it }
    ?.let { mapOf("mapping" to "initial-failure") } ?: emptyMap()
  )
