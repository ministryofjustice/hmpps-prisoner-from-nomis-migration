package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.model.SyncTransactionReceipt
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.SynchronisationMessageType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.SynchronisationMessageType.RETRY_SYNCHRONISATION_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TransactionMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.TransactionIdBufferRepository
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.InternalMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NotFoundException
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationType
import java.util.UUID

@Service
class TransactionSynchronisationService(
  private val nomisApiService: FinanceNomisApiService,
  private val transactionMappingService: TransactionMappingApiService,
  private val financeService: FinanceApiService,
  private val telemetryClient: TelemetryClient,
  private val queueService: SynchronisationQueueService,
  private val transactionIdBufferRepository: TransactionIdBufferRepository,
  @Value($$"${finance.transactions.forwardingDelaySeconds}")
  private val forwardingDelaySeconds: Int,
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun transactionIdCheck(event: TransactionEvent, requestId: UUID, eventType: String = "created") {
    if (transactionIdBufferRepository.existsById(event.transactionId)) {
      log.debug("Skipping synchronisation of transaction ${event.transactionId} as id already recorded")
      return
    }

    if (transactionIdBufferRepository.addId(event.transactionId) == 0) {
      log.debug("Skipping synchronisation of transaction ${event.transactionId} as failed to record id")
      return
    }

    try {
      queueService.sendMessage(
        messageType = SynchronisationMessageType.PERFORM_TRANSACTION_SYNC.name,
        synchronisationType = SynchronisationType.FINANCE,
        message = EncapsulatedTransaction(event, requestId, eventType),
        telemetryAttributes = mapOf("transactionId" to event.transactionId.toString()),
        delaySeconds = forwardingDelaySeconds,
      )
    } catch (e: Exception) {
      log.error("Unexpected error from queuing transaction ${event.transactionId}", e)
      transactionIdBufferRepository.deleteById(event.transactionId)
      throw e
    }
  }

  suspend fun transactionInserted(event: TransactionEvent, requestId: UUID) {
    if (event.originatesInDps) {
      telemetryClient.trackEvent("transactions-synchronisation-created-skipped", event.toTelemetryProperties())
      return
    }
    transactionIdCheck(event, requestId)
  }

  suspend fun transactionUpdated(event: TransactionEvent, requestId: UUID) {
    if (event.originatesInDps) {
      telemetryClient.trackEvent("transactions-synchronisation-updated-skipped", event.toTelemetryProperties())
      return
    }

    transactionMappingService.getMappingGivenNomisIdOrNull(event.transactionId)
      ?.let {
        transactionIdCheck(event, requestId, "updated")
      }
      ?: run {
        telemetryClient.trackEvent("transactions-synchronisation-updated-failed", event.toTelemetryProperties())
        throw IllegalStateException("Received OFFENDER_TRANSACTIONS-UPDATED for a transaction that has never been created")
      }
  }

  suspend fun transactionUpserted(encapsulatedTransaction: InternalMessage<EncapsulatedTransaction>) {
    val (event, requestId, eventType) = encapsulatedTransaction.body
    try {
      transactionIdBufferRepository.deleteById(event.transactionId)
      val mapping = transactionMappingService.getMappingGivenNomisIdOrNull(event.transactionId)
//      if (event.auditMissing()) {
      // TODO mapping may or may not exist anyway! Need another way to detect null audit name - if it ever happens
//        mapping
//          ?.apply {
//            if (mappingType == TransactionMappingDto.MappingType.DPS_CREATED) {
//              // Detected where the auditModuleName is null but the mapping exists, signifying that this one was created from DPS
//              telemetryClient.trackEvent(
//                "transactions-synchronisation-created-skipped-null",
//                event.toTelemetryProperties(),
//              )
//              return
//            }
//          }
//      }
      val nomisTransactions = nomisApiService.getPrisonerTransactions(event.transactionId)
      if (nomisTransactions.isEmpty()) {
        val glTransactions = nomisApiService.getGLTransactions(event.transactionId)
        if (glTransactions.isEmpty()) {
          throw NotFoundException("No GL transactions found in nomis for transactionId=$event.transactionId")
        } else {
          financeService.syncGeneralLedgerTransactions(
            glTransactions.toSyncGeneralLedgerTransactionRequest(requestId),
          )
            .also { financeResponse ->
              assertMappingExistenceMatchesAction(mapping, financeResponse)
              if (mapping == null) {
                val mappingResponse = maybeCreateTransactionMapping(event, financeResponse)

                telemetryClient.trackEvent(
                  "transactions-synchronisation-$eventType-success-gl",
                  event.toTelemetryProperties(
                    dpsTransactionId = financeResponse.synchronizedTransactionId,
                    mappingFailed = mappingResponse == MappingResponse.MAPPING_FAILED,
                  ),
                )
              } else {
                telemetryClient.trackEvent(
                  "transactions-synchronisation-$eventType-success-gl-additional",
                  event.toTelemetryProperties(
                    dpsTransactionId = financeResponse.synchronizedTransactionId,
                    mappingFailed = false,
                  ),
                )
              }
            }
        }
      } else {
        financeService.syncTransactions(
          nomisTransactions.toSyncOffenderTransactionRequest(requestId),
        )
          .also { financeResponse ->
            assertMappingExistenceMatchesAction(mapping, financeResponse)
            if (mapping == null) {
              val mappingResponse = maybeCreateTransactionMapping(event, financeResponse)

              telemetryClient.trackEvent(
                "transactions-synchronisation-$eventType-success",
                event.toTelemetryProperties(
                  dpsTransactionId = financeResponse.synchronizedTransactionId,
                  mappingFailed = mappingResponse == MappingResponse.MAPPING_FAILED,
                ),
              )
            } else {
              telemetryClient.trackEvent(
                "transactions-synchronisation-$eventType-success-additional",
                event.toTelemetryProperties(
                  dpsTransactionId = financeResponse.synchronizedTransactionId,
                  mappingFailed = false,
                ),
              )
            }
          }
      }
    } catch (e: Exception) {
      telemetryClient.trackEvent(
        "transactions-synchronisation-$eventType-failed",
        event.toTelemetryProperties() + mapOf("error" to (e.message ?: e.javaClass.toString())),
      )
      throw e
    }
  }

  suspend fun glTransactionInserted(event: GLTransactionEvent, requestId: UUID) {
    if (event.originatesInDps) {
      val telemetry = event.toTransactionEvent().toTelemetryProperties() +
        mapOf("glSequence" to event.gLEntrySequence.toString())
      telemetryClient.trackEvent("transactions-synchronisation-created-skipped", telemetry)
      return
    }
    transactionIdCheck(event.toTransactionEvent(), requestId)
  }

  suspend fun glTransactionUpdated(event: GLTransactionEvent, requestId: UUID) {
    if (event.originatesInDps) {
      val telemetry = event.toTransactionEvent().toTelemetryProperties() +
        mapOf("glSequence" to event.gLEntrySequence.toString())
      telemetryClient.trackEvent("transactions-synchronisation-updated-skipped", telemetry)
      return
    }

    transactionMappingService.getMappingGivenNomisIdOrNull(event.transactionId)
      ?.let {
        transactionIdCheck(event.toTransactionEvent(), requestId, "updated")
      }
      ?: run {
        telemetryClient.trackEvent("transactions-synchronisation-updated-failed", event.toTransactionEvent().toTelemetryProperties())
        throw IllegalStateException("Received GL_TRANSACTIONS-UPDATED for a transaction that has never been created")
      }
  }

  enum class MappingResponse {
    MAPPING_CREATED,
    MAPPING_FAILED,
  }

  suspend fun maybeCreateTransactionMapping(
    event: TransactionEvent,
    receipt: SyncTransactionReceipt,
  ): MappingResponse {
    val mapping = TransactionMappingDto(
      dpsTransactionId = receipt.synchronizedTransactionId.toString(),
      nomisTransactionId = event.transactionId,
      nomisBookingId = event.bookingId ?: 0, // TEMP hack while api is ironed out
      offenderNo = event.offenderIdDisplay,
      mappingType = TransactionMappingDto.MappingType.NOMIS_CREATED,
    )
    return try {
      transactionMappingService.createMapping(
        mapping,
        object : ParameterizedTypeReference<DuplicateErrorResponse<TransactionMappingDto>>() {},
      )
        .also { response ->
          if (response.isError) {
            val duplicateErrorDetails = (response.errorResponse!!).moreInfo
            telemetryClient.trackEvent(
              "transaction-from-nomis-synch-duplicate",
              mapOf(
                "duplicateDpsTransactionId" to duplicateErrorDetails.duplicate.dpsTransactionId,
                "duplicateNomisTransactionId" to duplicateErrorDetails.duplicate.nomisTransactionId.toString(),
                "existingDpsTransactionId" to duplicateErrorDetails.existing.dpsTransactionId,
                "existingNomisTransactionId" to duplicateErrorDetails.existing.nomisTransactionId.toString(),
              ),
            )
          }
        }
      MappingResponse.MAPPING_CREATED
    } catch (e: Exception) {
      log.error(
        "Failed to create mapping for dpsTransaction $receipt, nomisTransactionId ${event.transactionId}",
        e,
      )
      queueService.sendMessage(
        messageType = RETRY_SYNCHRONISATION_MAPPING.name,
        synchronisationType = SynchronisationType.FINANCE,
        message = mapping,
        telemetryAttributes = event.toTelemetryProperties(receipt.synchronizedTransactionId),
      )
      MappingResponse.MAPPING_FAILED
    }
  }

  suspend fun retryCreateTransactionMapping(retryMessage: InternalMessage<TransactionMappingDto>) {
    transactionMappingService.createMapping(
      retryMessage.body,
      object : ParameterizedTypeReference<DuplicateErrorResponse<TransactionMappingDto>>() {},
    ).also {
      telemetryClient.trackEvent(
        "transactions-mapping-created-synchronisation-success",
        retryMessage.telemetryAttributes,
      )
    }
  }
}

private fun GLTransactionEvent.toTransactionEvent() = TransactionEvent(
  transactionId = this.transactionId,
  entrySequence = this.entrySequence,
  caseload = this.caseload,
  transactionType = this.transactionType,
  offenderIdDisplay = this.offenderIdDisplay,
  bookingId = this.bookingId,
  auditModuleName = this.auditModuleName,
)

private fun assertMappingExistenceMatchesAction(
  mapping: TransactionMappingDto?,
  receipt: SyncTransactionReceipt,
) {
//  if ((mapping == null) != (receipt.action == SyncTransactionReceipt.Action.CREATED)) {
//    throw RuntimeException("Mismatch of mapping existence vs Action, mapping=$mapping, receipt=$receipt")
//  }
}

private fun TransactionEvent.toTelemetryProperties(
  dpsTransactionId: UUID? = null,
  mappingFailed: Boolean? = null,
): Map<String, String> = mapOf(
  "nomisTransactionId" to this.transactionId.toString(),
  "bookingId" to this.bookingId.toString(),
) +
  (offenderIdDisplay?.let { mapOf("offenderNo" to it) } ?: emptyMap()) +
  (dpsTransactionId?.let { mapOf("dpsTransactionId" to it.toString()) } ?: emptyMap()) +
  (if (mappingFailed == true) mapOf("mapping" to "initial-failure") else emptyMap())

data class EncapsulatedTransaction(val transactionEvent: TransactionEvent, val requestId: UUID, val eventType: String)
