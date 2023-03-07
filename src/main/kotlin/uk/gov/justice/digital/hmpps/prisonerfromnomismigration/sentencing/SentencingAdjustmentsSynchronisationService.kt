package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.trackEvent
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
        event.toTelemetryProperties()
      )
      return
    }
    nomisApiService.getSentenceAdjustment(event.adjustmentId).takeUnless { it.hiddenFromUsers }
      ?.also { nomisAdjustment ->
        sentencingAdjustmentsMappingService.findNomisSentencingAdjustmentMapping(
          nomisAdjustmentId = event.adjustmentId,
          nomisAdjustmentCategory = "SENTENCE"
        )?.let {
          sentencingService.updateSentencingAdjustment(it.adjustmentId, nomisAdjustment.toSentencingAdjustment())
          telemetryClient.trackEvent(
            "sentence-adjustment-updated-synchronisation-success",
            event.toTelemetryProperties(it.adjustmentId)
          )
        } ?: let {
          sentencingService.createSentencingAdjustment(nomisAdjustment.toSentencingAdjustment()).also { adjustment ->
            tryToCreateSentenceMapping(event, adjustment.id).also { result ->
              telemetryClient.trackEvent(
                "sentence-adjustment-created-synchronisation-success",
                event.toTelemetryProperties(adjustment.id, result == MAPPING_FAILED),
              )
            }
          }
        }
      } ?: also {
      telemetryClient.trackEvent(
        "sentence-adjustment-hidden-synchronisation-skipped",
        event.toTelemetryProperties()
      )
    }
  }

  suspend fun synchroniseSentenceAdjustmentDelete(event: SentenceAdjustmentOffenderEvent) {
    if (event.auditModuleName == "DPS_SYNCHRONISATION") {
      telemetryClient.trackEvent(
        "sentence-adjustment-delete-synchronisation-skipped",
        event.toTelemetryProperties()
      )
      return
    }
    sentencingAdjustmentsMappingService.findNomisSentencingAdjustmentMapping(
      nomisAdjustmentId = event.adjustmentId,
      nomisAdjustmentCategory = "SENTENCE"
    )?.let {
      sentencingService.deleteSentencingAdjustment(it.adjustmentId)
      sentencingAdjustmentsMappingService.deleteNomisSentenceAdjustmentMapping(it.adjustmentId)
      telemetryClient.trackEvent(
        "sentence-adjustment-delete-synchronisation-success",
        event.toTelemetryProperties(adjustmentId = it.adjustmentId)
      )
    } ?: let {
      telemetryClient.trackEvent(
        "sentence-adjustment-delete-synchronisation-ignored",
        event.toTelemetryProperties()
      )
    }
  }

  suspend fun synchroniseKeyDateAdjustmentCreateOrUpdate(event: KeyDateAdjustmentOffenderEvent) {
    if (event.auditModuleName == "DPS_SYNCHRONISATION") {
      telemetryClient.trackEvent(
        "key-date-adjustment-synchronisation-skipped",
        event.toTelemetryProperties()
      )
      return
    }
    val nomisAdjustment = nomisApiService.getKeyDateAdjustment(event.adjustmentId)
    sentencingAdjustmentsMappingService.findNomisSentencingAdjustmentMapping(
      nomisAdjustmentId = event.adjustmentId,
      nomisAdjustmentCategory = "KEY-DATE"
    )?.let {
      sentencingService.updateSentencingAdjustment(it.adjustmentId, nomisAdjustment.toSentencingAdjustment())
      telemetryClient.trackEvent(
        "key-date-adjustment-updated-synchronisation-success",
        event.toTelemetryProperties(it.adjustmentId)
      )
    } ?: let {
      sentencingService.createSentencingAdjustment(nomisAdjustment.toSentencingAdjustment()).also { adjustment ->
        tryToCreateKeyDateMapping(event, adjustment.id).also { result ->
          telemetryClient.trackEvent(
            "key-date-adjustment-created-synchronisation-success",
            event.toTelemetryProperties(adjustment.id, result == MAPPING_FAILED),
          )
        }
      }
    }
  }

  suspend fun synchroniseKeyDateAdjustmentDelete(event: KeyDateAdjustmentOffenderEvent) {
    if (event.auditModuleName == "DPS_SYNCHRONISATION") {
      telemetryClient.trackEvent(
        "key-date-adjustment-delete-synchronisation-skipped",
        event.toTelemetryProperties()
      )
      return
    }
    sentencingAdjustmentsMappingService.findNomisSentencingAdjustmentMapping(
      nomisAdjustmentId = event.adjustmentId,
      nomisAdjustmentCategory = "KEY-DATE"
    )?.let {
      sentencingService.deleteSentencingAdjustment(it.adjustmentId)
      sentencingAdjustmentsMappingService.deleteNomisSentenceAdjustmentMapping(it.adjustmentId)
      telemetryClient.trackEvent(
        "key-date-adjustment-delete-synchronisation-success",
        event.toTelemetryProperties(adjustmentId = it.adjustmentId)
      )
    } ?: let {
      telemetryClient.trackEvent(
        "key-date-adjustment-delete-synchronisation-ignored",
        event.toTelemetryProperties()
      )
    }
  }

  enum class MappingResponse {
    MAPPING_CREATED,
    MAPPING_FAILED
  }

  suspend fun tryToCreateSentenceMapping(
    event: SentenceAdjustmentOffenderEvent,
    adjustmentId: String
  ): MappingResponse =
    try {
      sentencingAdjustmentsMappingService.createNomisSentencingAdjustmentSynchronisationMapping(
        nomisAdjustmentId = event.adjustmentId,
        nomisAdjustmentCategory = "SENTENCE",
        adjustmentId = adjustmentId
      ).also {
        if (it.isError) {
          val duplicateErrorDetails = it.errorResponse!!.moreInfo
          telemetryClient.trackEvent(
            "from-nomis-synch-adjustment-duplicate",
            mapOf<String, String>(
              "duplicateAdjustmentId" to duplicateErrorDetails.duplicateAdjustment.adjustmentId,
              "duplicateNomisAdjustmentId" to duplicateErrorDetails.duplicateAdjustment.nomisAdjustmentId.toString(),
              "duplicateNomisAdjustmentCategory" to duplicateErrorDetails.duplicateAdjustment.nomisAdjustmentCategory,
              "existingAdjustmentId" to duplicateErrorDetails.existingAdjustment.adjustmentId,
              "existingNomisAdjustmentId" to duplicateErrorDetails.existingAdjustment.nomisAdjustmentId.toString(),
              "existingNomisAdjustmentCategory" to duplicateErrorDetails.existingAdjustment.nomisAdjustmentCategory
            ),
            null
          )
        }
      }
      MAPPING_CREATED
    } catch (e: Exception) {
      log.error(
        "Failed to create mapping for adjustment id $adjustmentId, nomisAdjustmentId ${event.adjustmentId}, nomisAdjustmentCategory SENTENCE",
        e
      )
      queueService.sendMessage(
        messageType = SynchronisationMessageType.RETRY_SYNCHRONISATION_MAPPING.name,
        synchronisationType = SynchronisationType.SENTENCING_ADJUSTMENTS,
        message = SentencingAdjustmentNomisMapping(
          nomisAdjustmentId = event.adjustmentId,
          nomisAdjustmentCategory = "SENTENCE",
          adjustmentId = adjustmentId,
          mappingType = "NOMIS_CREATED"
        ),
        telemetryAttributes = event.toTelemetryProperties(adjustmentId)
      )
      MAPPING_FAILED
    }

  suspend fun tryToCreateKeyDateMapping(
    event: KeyDateAdjustmentOffenderEvent,
    adjustmentId: String
  ): MappingResponse =
    try {
      sentencingAdjustmentsMappingService.createNomisSentencingAdjustmentSynchronisationMapping(
        nomisAdjustmentId = event.adjustmentId,
        nomisAdjustmentCategory = "KEY-DATE",
        adjustmentId = adjustmentId
      )
      MAPPING_CREATED
    } catch (e: Exception) {
      log.error(
        "Failed to create mapping for adjustment id $adjustmentId, nomisAdjustmentId ${event.adjustmentId}, nomisAdjustmentCategory KEY-DATE",
        e
      )
      queueService.sendMessage(
        messageType = SynchronisationMessageType.RETRY_SYNCHRONISATION_MAPPING.name,
        synchronisationType = SynchronisationType.SENTENCING_ADJUSTMENTS,
        message = SentencingAdjustmentNomisMapping(
          nomisAdjustmentId = event.adjustmentId,
          nomisAdjustmentCategory = "KEY-DATE",
          adjustmentId = adjustmentId,
          mappingType = "NOMIS_CREATED"
        ),
        telemetryAttributes = event.toTelemetryProperties(adjustmentId)
      )
      MAPPING_FAILED
    }

  suspend fun retryCreateSentenceAdjustmentMapping(retryMessage: InternalMessage<SentencingAdjustmentNomisMapping>) {
    sentencingAdjustmentsMappingService.createNomisSentencingAdjustmentSynchronisationMapping(
      nomisAdjustmentId = retryMessage.body.nomisAdjustmentId,
      nomisAdjustmentCategory = retryMessage.body.nomisAdjustmentCategory,
      adjustmentId = retryMessage.body.adjustmentId
    ).also {
      telemetryClient.trackEvent(
        "adjustment-mapping-created-synchronisation-success",
        retryMessage.telemetryAttributes
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
