package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.SyncSentenceAdjustment
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.NomisPrisonerMergeEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.SynchronisationMessageType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.api.SentencingMappingResourceApi.NomisAdjustmentCategoryGetSentenceAdjustmentMappingGivenNomisId.KEYMinusDATE
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.api.SentencingMappingResourceApi.NomisAdjustmentCategoryGetSentenceAdjustmentMappingGivenNomisId.SENTENCE
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.SentencingAdjustmentMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.SentencingAdjustmentMappingDto.MappingType.NOMIS_CREATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.SentencingAdjustmentMappingDto.NomisAdjustmentCategory
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.KeyDateAdjustmentResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.SentenceAdjustmentResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing.SentencingAdjustmentsSynchronisationService.MappingResponse.MAPPING_CREATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing.SentencingAdjustmentsSynchronisationService.MappingResponse.MAPPING_FAILED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.InternalMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationType

@Service
class SentencingAdjustmentsSynchronisationService(
  private val sentencingAdjustmentsMappingService: SentencingAdjustmentsMappingService,
  private val sentencingAdjustmentsNomisApiService: SentencingAdjustmentsNomisApiService,
  private val sentencingService: SentencingService,
  private val telemetryClient: TelemetryClient,
  private val queueService: SynchronisationQueueService,
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun synchroniseSentenceAdjustmentCreateOrUpdate(event: SentenceAdjustmentOffenderEvent) {
    if (event.originatesInDps) {
      telemetryClient.trackEvent(
        "sentence-adjustment-synchronisation-skipped",
        event.toTelemetryProperties(),
      )
      return
    }
    createOrUpdateSentenceAdjustment(
      SentenceAdjustmentUpdateOrCreateRequest(
        offenderNumber = event.offenderIdDisplay,
        bookingId = event.bookingId,
        sentenceSeq = event.sentenceSeq,
        adjustmentId = event.adjustmentId,
      ),
    )
  }
  suspend fun createOrUpdateSentenceAdjustment(request: SentenceAdjustmentUpdateOrCreateRequest) {
    sentencingAdjustmentsNomisApiService.getSentenceAdjustment(request.adjustmentId)?.takeUnless { it.hiddenFromUsers }
      ?.also { nomisAdjustment ->
        sentencingAdjustmentsMappingService.findNomisSentencingAdjustmentMappingOrNull(
          nomisAdjustmentId = request.adjustmentId,
          nomisAdjustmentCategory = SENTENCE,
        )?.let {
          sentencingService.updateSentencingAdjustment(it.adjustmentId, nomisAdjustment.toSentencingAdjustment())
          telemetryClient.trackEvent(
            "sentence-adjustment-updated-synchronisation-success",
            request.toTelemetryProperties(it.adjustmentId),
          )
        } ?: let {
          sentencingService.createSentencingAdjustment(nomisAdjustment.toSentencingAdjustment()).also { adjustment ->
            tryToCreateSentenceMapping(request, adjustment.adjustmentId.toString()).also { result ->
              telemetryClient.trackEvent(
                "sentence-adjustment-created-synchronisation-success",
                request.toTelemetryProperties(adjustment.adjustmentId.toString(), result == MAPPING_FAILED),
              )
            }
          }
        }
      } ?: also {
      telemetryClient.trackEvent(
        "sentence-adjustment-hidden-or-deleted-synchronisation-skipped",
        request.toTelemetryProperties(),
      )
    }
  }

  suspend fun nomisSentenceAdjustmentsUpdate(event: SyncSentenceAdjustment) {
    event.sentences.forEach { sentence ->
      sentence.adjustmentIds.forEach { adjustmentId ->
        createOrUpdateSentenceAdjustment(
          request = SentenceAdjustmentUpdateOrCreateRequest(
            offenderNumber = event.offenderNo,
            bookingId = sentence.sentenceId.offenderBookingId,
            sentenceSeq = sentence.sentenceId.sentenceSequence,
            adjustmentId = adjustmentId,
          ),
        )
      }
    }
  }
  suspend fun synchroniseSentenceAdjustmentDelete(event: SentenceAdjustmentOffenderEvent) {
    sentencingAdjustmentsMappingService.findNomisSentencingAdjustmentMappingOrNull(
      nomisAdjustmentId = event.adjustmentId,
      nomisAdjustmentCategory = SENTENCE,
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
    if (event.originatesInDps) {
      telemetryClient.trackEvent(
        "key-date-adjustment-synchronisation-skipped",
        event.toTelemetryProperties(),
      )
      return
    }
    sentencingAdjustmentsNomisApiService.getKeyDateAdjustment(event.adjustmentId)?.also { nomisAdjustment ->
      sentencingAdjustmentsMappingService.findNomisSentencingAdjustmentMappingOrNull(
        nomisAdjustmentId = event.adjustmentId,
        nomisAdjustmentCategory = KEYMinusDATE,
      )?.let {
        sentencingService.updateSentencingAdjustment(it.adjustmentId, nomisAdjustment.toSentencingAdjustment())
        telemetryClient.trackEvent(
          "key-date-adjustment-updated-synchronisation-success",
          event.toTelemetryProperties(it.adjustmentId),
        )
      } ?: let {
        sentencingService.createSentencingAdjustment(nomisAdjustment.toSentencingAdjustment()).also { adjustment ->
          tryToCreateKeyDateMapping(event, adjustment.adjustmentId.toString()).also { result ->
            telemetryClient.trackEvent(
              "key-date-adjustment-created-synchronisation-success",
              event.toTelemetryProperties(adjustment.adjustmentId.toString(), result == MAPPING_FAILED),
            )
          }
        }
      }
    } ?: also {
      telemetryClient.trackEvent(
        "key-date-adjustment-deleted-synchronisation-skipped",
        event.toTelemetryProperties(),
      )
    }
  }

  suspend fun synchroniseKeyDateAdjustmentDelete(event: KeyDateAdjustmentOffenderEvent) {
    sentencingAdjustmentsMappingService.findNomisSentencingAdjustmentMappingOrNull(
      nomisAdjustmentId = event.adjustmentId,
      nomisAdjustmentCategory = KEYMinusDATE,
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
    event: SentenceAdjustmentUpdateOrCreateRequest,
    adjustmentId: String,
  ): MappingResponse {
    val mapping = SentencingAdjustmentMappingDto(
      nomisAdjustmentId = event.adjustmentId,
      nomisAdjustmentCategory = NomisAdjustmentCategory.SENTENCE,
      adjustmentId = adjustmentId,
      mappingType = NOMIS_CREATED,
    )
    try {
      sentencingAdjustmentsMappingService.createMapping(
        mapping,
        object : ParameterizedTypeReference<DuplicateErrorResponse<SentencingAdjustmentMappingDto>>() {},
      ).also {
        if (it.isError) {
          it.errorResponse!!.trackDuplicate()
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
    val mapping = SentencingAdjustmentMappingDto(
      nomisAdjustmentId = event.adjustmentId,
      nomisAdjustmentCategory = NomisAdjustmentCategory.KEYMinusDATE,
      adjustmentId = adjustmentId,
      mappingType = NOMIS_CREATED,
    )
    try {
      sentencingAdjustmentsMappingService.createMapping(
        mapping,
        object : ParameterizedTypeReference<DuplicateErrorResponse<SentencingAdjustmentMappingDto>>() {},
      ).also {
        if (it.isError) {
          it.errorResponse!!.trackDuplicate()
        }
      }
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

  suspend fun retryCreateSentenceAdjustmentMapping(retryMessage: InternalMessage<SentencingAdjustmentMappingDto>) {
    sentencingAdjustmentsMappingService.createMapping(
      retryMessage.body,
      object : ParameterizedTypeReference<DuplicateErrorResponse<SentencingAdjustmentMappingDto>>() {},
    ).also {
      if (it.isError) {
        it.errorResponse!!.trackDuplicate()
      } else {
        telemetryClient.trackEvent(
          "adjustment-mapping-created-synchronisation-success",
          retryMessage.telemetryAttributes,
        )
      }
    }
  }

  suspend fun synchronisePrisonerMerge(prisonerMergeEvent: NomisPrisonerMergeEvent) {
    val adjustmentsInNomis = sentencingAdjustmentsNomisApiService.getAllByBookingId(prisonerMergeEvent.bookingId)

    val sentenceAdjustmentsCreated = adjustmentsInNomis.sentenceAdjustments.mapNotNull { adjustment ->
      adjustment.takeIf { doesNotExist(it) }?.also {
        createAdjustment(prisonerMergeEvent, adjustment)
      }
    }
    val keyDateAdjustmentsCreated = adjustmentsInNomis.keyDateAdjustments.mapNotNull { adjustment ->
      adjustment.takeIf { doesNotExist(it) }?.also {
        createAdjustment(prisonerMergeEvent, adjustment)
      }
    }

    telemetryClient.trackEvent(
      "from-nomis-synch-adjustment-merge",
      mapOf(
        "bookingId" to prisonerMergeEvent.bookingId.toString(),
        "sentenceAdjustments" to adjustmentsInNomis.sentenceAdjustments.size.toString(),
        "keyDateAdjustments" to adjustmentsInNomis.keyDateAdjustments.size.toString(),
        "sentenceAdjustmentsCreated" to sentenceAdjustmentsCreated.size.toString(),
        "keyDateAdjustmentsCreated" to keyDateAdjustmentsCreated.size.toString(),
      ),
    )
  }

  suspend fun repairPostMergeAdjustments(bookingId: Long) = synchronisePrisonerMerge(NomisPrisonerMergeEvent(bookingId))

  suspend fun repairAdjustmentsByBooking(bookingId: Long) {
    val adjustmentsInNomis = sentencingAdjustmentsNomisApiService.getAllByBookingId(bookingId)
    adjustmentsInNomis.sentenceAdjustments.forEach { adjustment ->
      synchroniseSentenceAdjustmentCreateOrUpdate(
        event = SentenceAdjustmentOffenderEvent(
          offenderIdDisplay = adjustment.offenderNo,
          bookingId = bookingId,
          sentenceSeq = adjustment.sentenceSequence,
          adjustmentId = adjustment.id,
          auditModuleName = "REPAIR",
        ),
      )
    }
    adjustmentsInNomis.keyDateAdjustments.forEach { adjustment ->
      synchroniseKeyDateAdjustmentCreateOrUpdate(
        event = KeyDateAdjustmentOffenderEvent(
          offenderIdDisplay = adjustment.offenderNo,
          bookingId = bookingId,
          adjustmentId = adjustment.id,
          auditModuleName = "REPAIR",
        ),
      )
    }
  }

  private suspend fun SentencingAdjustmentsSynchronisationService.createAdjustment(
    prisonerMergeEvent: NomisPrisonerMergeEvent,
    adjustment: SentenceAdjustmentResponse,
  ) {
    synchroniseSentenceAdjustmentCreateOrUpdate(
      SentenceAdjustmentOffenderEvent(
        bookingId = prisonerMergeEvent.bookingId,
        sentenceSeq = adjustment.sentenceSequence,
        adjustmentId = adjustment.id,
        offenderIdDisplay = adjustment.offenderNo,
        auditModuleName = "MERGE",
      ),
    )
  }
  private suspend fun SentencingAdjustmentsSynchronisationService.createAdjustment(
    prisonerMergeEvent: NomisPrisonerMergeEvent,
    adjustment: KeyDateAdjustmentResponse,
  ) {
    synchroniseKeyDateAdjustmentCreateOrUpdate(
      KeyDateAdjustmentOffenderEvent(
        bookingId = prisonerMergeEvent.bookingId,
        adjustmentId = adjustment.id,
        offenderIdDisplay = adjustment.offenderNo,
        auditModuleName = "MERGE",
      ),
    )
  }

  private suspend fun doesNotExist(adjustment: SentenceAdjustmentResponse) = sentencingAdjustmentsMappingService.findNomisSentencingAdjustmentMappingOrNull(
    nomisAdjustmentId = adjustment.id,
    nomisAdjustmentCategory = SENTENCE,
  ) == null
  private suspend fun doesNotExist(adjustment: KeyDateAdjustmentResponse) = sentencingAdjustmentsMappingService.findNomisSentencingAdjustmentMappingOrNull(
    nomisAdjustmentId = adjustment.id,
    nomisAdjustmentCategory = KEYMinusDATE,
  ) == null

  private fun DuplicateErrorResponse<SentencingAdjustmentMappingDto>.trackDuplicate() {
    val duplicateErrorDetails = (this).moreInfo
    telemetryClient.trackEvent(
      "from-nomis-synch-adjustment-duplicate",
      mapOf<String, String>(
        "duplicateAdjustmentId" to duplicateErrorDetails.duplicate.adjustmentId,
        "duplicateNomisAdjustmentId" to duplicateErrorDetails.duplicate.nomisAdjustmentId.toString(),
        "duplicateNomisAdjustmentCategory" to duplicateErrorDetails.duplicate.nomisAdjustmentCategory.value,
        "existingAdjustmentId" to duplicateErrorDetails.existing.adjustmentId,
        "existingNomisAdjustmentId" to duplicateErrorDetails.existing.nomisAdjustmentId.toString(),
        "existingNomisAdjustmentCategory" to duplicateErrorDetails.existing.nomisAdjustmentCategory.value,
      ),
      null,
    )
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

data class SentenceAdjustmentUpdateOrCreateRequest(
  val offenderNumber: String,
  val bookingId: Long,
  val sentenceSeq: Long,
  val adjustmentId: Long,
)

private fun SentenceAdjustmentUpdateOrCreateRequest.toTelemetryProperties(
  adjustmentId: String? = null,
  mappingFailed: Boolean? = null,
) = mapOf(
  "offenderNo" to this.offenderNumber,
  "bookingId" to this.bookingId.toString(),
  "sentenceSequence" to this.sentenceSeq.toString(),
  "nomisAdjustmentId" to this.adjustmentId.toString(),
  "adjustmentCategory" to "SENTENCE",
) + (adjustmentId?.let { mapOf("adjustmentId" to it) } ?: emptyMap()) + (
  mappingFailed?.takeIf { it }
    ?.let { mapOf("mapping" to "initial-failure") } ?: emptyMap()
  )
