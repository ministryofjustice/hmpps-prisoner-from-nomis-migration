package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.SynchronisationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing.SentencingSynchronisationService.MappingResponse.MAPPING_CREATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing.SentencingSynchronisationService.MappingResponse.MAPPING_FAILED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationType

@Service
class SentencingSynchronisationService(
  private val sentencingMappingService: SentencingMappingService,
  private val nomisApiService: NomisApiService,
  private val sentencingService: SentencingService,
  private val telemetryClient: TelemetryClient,
  private val queueService: MigrationQueueService,
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun synchroniseSentenceAdjustmentCreateOrUpdate(event: SentenceAdjustmentUpsertedOffenderEvent) {
    if (event.auditModuleName == "DPS_SYNCHRONISATION") {
      telemetryClient.trackEvent(
        "sentence-adjustment-synchronisation-skipped",
        event.toTelemetryProperties()
      )
      return
    }
    val nomisAdjustment = nomisApiService.getSentenceAdjustment(event.adjustmentId)
    sentencingMappingService.findNomisSentencingAdjustmentMapping(
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
        tryToCreateMapping(event, adjustment.id).also { result ->
          telemetryClient.trackEvent(
            "sentence-adjustment-created-synchronisation-success",
            event.toTelemetryProperties(adjustment.id, result == MAPPING_FAILED),
          )
        }
      }
    }
  }

  enum class MappingResponse {
    MAPPING_CREATED,
    MAPPING_FAILED
  }

  suspend fun tryToCreateMapping(
    event: SentenceAdjustmentUpsertedOffenderEvent,
    adjustmentId: String
  ): MappingResponse =
    try {
      sentencingMappingService.createNomisSentencingAdjustmentSynchronisationMapping(
        nomisAdjustmentId = event.adjustmentId,
        nomisAdjustmentCategory = "SENTENCE",
        adjustmentId = adjustmentId
      )
      MAPPING_CREATED
    } catch (e: Exception) {
      log.error(
        "Failed to create mapping for adjustment id $adjustmentId, nomisAdjustmentId ${event.adjustmentId}, nomisAdjustmentCategory SENTENCE",
        e
      )
      queueService.sendMessage(
        SentencingMessages.RETRY_SYNCHRONISATION_SENTENCING_ADJUSTMENT_MAPPING,
        SynchronisationContext(
          type = SynchronisationType.SENTENCING,
          telemetryProperties = event.toTelemetryProperties(adjustmentId),
          body = SentencingAdjustmentMapping(
            nomisAdjustmentId = event.adjustmentId,
            nomisAdjustmentCategory = "SENTENCE",
            adjustmentId = adjustmentId
          )
        )
      )
      MAPPING_FAILED
    }

  suspend fun retryCreateSentenceAdjustmentMapping(context: SynchronisationContext<SentencingAdjustmentMapping>) {
    sentencingMappingService.createNomisSentencingAdjustmentSynchronisationMapping(
      nomisAdjustmentId = context.body.nomisAdjustmentId,
      nomisAdjustmentCategory = context.body.nomisAdjustmentCategory,
      adjustmentId = context.body.adjustmentId
    ).also {
      telemetryClient.trackEvent(
        "adjustment-mapping-created-synchronisation-success",
        context.telemetryProperties
      )
    }
  }
}

private fun SentenceAdjustmentUpsertedOffenderEvent.toTelemetryProperties(
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
