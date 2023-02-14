package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service

@Service
class SentencingSynchronisationService(
  private val sentencingMappingService: SentencingMappingService,
  private val telemetryClient: TelemetryClient
) {
  suspend fun synchroniseSentenceAdjustmentCreateOrUpdate(event: SentenceAdjustmentUpsertedOffenderEvent) {
    sentencingMappingService.findNomisSentencingAdjustmentMapping(
      nomisAdjustmentId = event.adjustmentId,
      nomisAdjustmentCategory = "SENTENCE"
    )?.let {
      telemetryClient.trackEvent(
        "sentence-adjustment-updated-synchronisation-success",
        mapOf(
          "offenderNo" to event.offenderIdDisplay,
          "bookingId" to event.bookingId.toString(),
          "sentenceSequence" to event.sentenceSeq.toString(),
          "nomisAdjustmentId" to event.adjustmentId.toString(),
          "adjustmentCategory" to "SENTENCE",
          "adjustmentId" to it.adjustmentId,
        ),
        null
      )
    } ?: let {
      telemetryClient.trackEvent(
        "sentence-adjustment-created-synchronisation-success",
        mapOf(
          "offenderNo" to event.offenderIdDisplay,
          "bookingId" to event.bookingId.toString(),
          "sentenceSequence" to event.sentenceSeq.toString(),
          "adjustmentCategory" to "SENTENCE",
          "nomisAdjustmentId" to event.adjustmentId.toString(),
        ),
        null
      )
    }
  }
}
