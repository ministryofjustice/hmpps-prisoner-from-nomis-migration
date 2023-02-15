package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService

@Service
class SentencingSynchronisationService(
  private val sentencingMappingService: SentencingMappingService,
  private val nomisApiService: NomisApiService,
  private val sentencingService: SentencingService,
  private val telemetryClient: TelemetryClient
) {
  suspend fun synchroniseSentenceAdjustmentCreateOrUpdate(event: SentenceAdjustmentUpsertedOffenderEvent) {
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
      sentencingService.createSentencingAdjustment(nomisAdjustment.toSentencingAdjustment()).also {
        sentencingMappingService.createNomisSentencingAdjustmentSynchronisationMapping(
          nomisAdjustmentId = event.adjustmentId,
          nomisAdjustmentCategory = "SENTENCE",
          adjustmentId = it.id
        )
        telemetryClient.trackEvent(
          "sentence-adjustment-created-synchronisation-success",
          event.toTelemetryProperties(it.id),
        )
      }
    }
  }
}

private fun SentenceAdjustmentUpsertedOffenderEvent.toTelemetryProperties(adjustmentId: String) = mapOf(
  "offenderNo" to this.offenderIdDisplay,
  "bookingId" to this.bookingId.toString(),
  "sentenceSequence" to this.sentenceSeq.toString(),
  "nomisAdjustmentId" to this.adjustmentId.toString(),
  "adjustmentCategory" to "SENTENCE",
  "adjustmentId" to adjustmentId,
)
