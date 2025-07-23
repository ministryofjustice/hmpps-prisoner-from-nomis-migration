package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.model.SyncTransactionReceipt
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.SynchronisationMessageType.RETRY_SYNCHRONISATION_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TransactionMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.InternalMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NotFoundException
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationType
import java.util.*

@Service
class TransactionSynchronisationService(
  private val nomisApiService: TransactionNomisApiService,
  private val transactionMappingService: TransactionMappingApiService,
  private val financeService: FinanceApiService,
  private val telemetryClient: TelemetryClient,
  private val queueService: SynchronisationQueueService,
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun transactionInserted(event: TransactionEvent, requestId: UUID) {
    try {
      if (event.isSourcedFromDPS()) {
        telemetryClient.trackEvent("transactions-synchronisation-created-skipped", event.toTelemetryProperties())
        return
      }
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
      val nomisTransactions = nomisApiService.getTransactions(event.transactionId)
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
                  "transactions-synchronisation-created-success-gl",
                  event.toTelemetryProperties(
                    dpsTransactionId = financeResponse.synchronizedTransactionId,
                    mappingFailed = mappingResponse == MappingResponse.MAPPING_FAILED,
                  ),
                )
              } else {
                telemetryClient.trackEvent(
                  "transactions-synchronisation-created-success-gl-additional",
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
                "transactions-synchronisation-created-success",
                event.toTelemetryProperties(
                  dpsTransactionId = financeResponse.synchronizedTransactionId,
                  mappingFailed = mappingResponse == MappingResponse.MAPPING_FAILED,
                ),
              )
            } else {
              telemetryClient.trackEvent(
                "transactions-synchronisation-created-success-additional",
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
        "transactions-synchronisation-created-failed",
        event.toTelemetryProperties() + mapOf("error" to (e.message ?: e.javaClass.toString())),
      )
      throw e
    }
  }

  suspend fun glTransactionInserted(event: GLTransactionEvent, requestId: UUID) {
    transactionInserted(event.toTransactionEvent(), requestId)
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
) = mapOf(
  "nomisTransactionId" to this.transactionId.toString(),
  "offenderNo" to this.offenderIdDisplay,
  "bookingId" to this.bookingId.toString(),
) + (dpsTransactionId?.let { mapOf("dpsTransactionId" to it.toString()) } ?: emptyMap()) + (
  if (mappingFailed == true) mapOf("mapping" to "initial-failure") else emptyMap()
  )

private fun TransactionEvent.isSourcedFromDPS() = auditModuleName == "DPS_SYNCHRONISATION"
private fun TransactionEvent.auditMissing() = auditModuleName == null
