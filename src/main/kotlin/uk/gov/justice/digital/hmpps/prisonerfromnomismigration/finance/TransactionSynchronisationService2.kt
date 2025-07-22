package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationQueueService
import java.util.*

@Service
class TransactionSynchronisationService2(
  private val nomisApiService: TransactionNomisApiService,
  private val transactionMappingService: TransactionMappingApiService,
  private val financeService: FinanceApiService,
  private val telemetryClient: TelemetryClient,
  private val queueService: SynchronisationQueueService,
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun glTransactionInserted(event: TransactionEvent, requestId: UUID) {
  }

//  private suspend fun updateDps(
//    event: TransactionsEvent,
//    nomisTransaction: TransactionResponse,
//  ): TransactionMappingDto? = transactionMappingService.getMappingGivenNomisIdOrNull(event.transactionId)
//    ?.also { mapping ->
//      financeService.upsertTransaction(
//        nomisTransaction.toDPSSyncTransaction(
//          event.offenderIdDisplay!!,
//          UUID.fromString(mapping.dpsTransactionId),
//        ),
//      )
//    }
//
//  private suspend fun updateRelatedNomisTransactions(
//    mapping: TransactionMappingDto,
//    event: TransactionsEvent,
//    nomisTransaction: TransactionResponse,
//  ) {
//    transactionMappingService.getByDpsId(mapping.dpsTransactionId)
//      .filter { it.nomisTransactionId != event.transactionId }
//      .map { otherNomisTransaction ->
//        nomisApiService.updateTransaction(
//          otherNomisTransaction.nomisTransactionId,
//          UpdateTransactionRequest(
//            text = nomisTransaction.transactionText,
//            amendments = nomisTransaction.amendments.map {
//              UpdateAmendment(
//                text = it.text,
//                authorUsername = it.authorUsername,
//                createdDateTime = it.createdDateTime,
//              )
//            },
//          ),
//        )
//        telemetryClient.trackEvent(
//          "transaction-synchronisation-updated-related-success",
//          mapOf(
//            "nomisTransactionId" to otherNomisTransaction.nomisTransactionId.toString(),
//            "offenderNo" to event.offenderIdDisplay.toString(),
//            "bookingId" to otherNomisTransaction.nomisBookingId.toString(),
//            "dpsTransactionId" to mapping.dpsTransactionId,
//          ),
//        )
//      }
//  }
//
//  suspend fun repairDeletedTransaction(nomisTransactionId: Long) = transactionDeleted(TransactionsEvent(transactionId = nomisTransactionId))
//
//
//  suspend fun synchronisePrisonerMerged(prisonerMergeEvent: PrisonerMergeDomainEvent) {
//    /*
//
// Some of the current booking's case notes have audit_module_name = 'MERGE'
// Work out the old booking id as being that which has copies of these case notes
// The affected existing mappings are those for the old prisoner and booking id for which just the prisoner is corrected .
// Also add new mappings for the new booking id for the copied case notes, which point to the same DPS CNs as the unaffected existing mappings.
//
//     */
//    val (nomsNumber, removedNomsNumber, bookingId) = prisonerMergeEvent.additionalInformation
//    try {
//      val nomisTransactions = nomisApiService.getTransactionsForPrisoner(nomsNumber).transactions
//      val freshlyMergedNomisTransactions = nomisTransactions
//        .filter { isMergeTransactionRecentlyCreated(it, prisonerMergeEvent) }
//        .apply {
//          if (isEmpty()) {
//            throw IllegalStateException("Merge data not ready for $nomsNumber")
//          }
//        }
//
//      val existingMappings = transactionMappingService.getMappings(nomisTransactions.map { it.transactionId })
//        .associateBy { it.nomisTransactionId }
//
//      // Skip if mappings already created
//      if (existingMappings.size == nomisTransactions.size) {
//        telemetryClient.trackEvent(
//          "transaction-prisoner-merge-skipped",
//          mapOf(
//            "offenderNo" to nomsNumber,
//            "removedOffenderNo" to removedNomsNumber,
//            "bookingId" to bookingId,
//          ),
//        )
//        return
//      }
//
//      // mapping of freshly merged case notes to what the note is a copy of
//      val newToOldMap = freshlyMergedNomisTransactions
//        .associate { newTransaction ->
//          newTransaction.transactionId to nomisTransactions.first { old -> isMergeCopy(old, newTransaction) }
//        }
//
//      transactionMappingService.updateMappingsByNomisId(removedNomsNumber, nomsNumber)
//
//      val newMappings =
//        freshlyMergedNomisTransactions.map { newNomisTransaction ->
//          TransactionMappingDto(
//            dpsTransactionId = existingMappings[newToOldMap[newNomisTransaction.transactionId]?.transactionId]
//              ?.dpsTransactionId
//              ?: throw IllegalStateException("synchronisePrisonerMerged(): No mapping found for newNomisTransaction = $newNomisTransaction, offender $nomsNumber"),
//            nomisTransactionId = newNomisTransaction.transactionId,
//            nomisBookingId = bookingId,
//            offenderNo = nomsNumber,
//            mappingType = NOMIS_CREATED,
//          )
//        }
//
//      // Now exclude any mappings that already exist.  This can happen if two merges occur within half an hour of each
//      // other as we find all merged case notes within that time frame
//      val newUniqueMappings = newMappings.filter { it.nomisTransactionId !in existingMappings.keys }
//      if (newUniqueMappings.size != newMappings.size) {
//        telemetryClient.trackEvent(
//          "transaction-prisoner-merge-existing-mappings",
//          mapOf(
//            "offenderNo" to nomsNumber,
//            "removedOffenderNo" to removedNomsNumber,
//            "bookingId" to bookingId,
//            "mappingsCount" to (newMappings.size - newUniqueMappings.size),
//            "mappings" to newMappings.filter { it.nomisTransactionId in existingMappings.keys }.joinToString { it.nomisTransactionId.toString() },
//          ),
//        )
//      }
//
//      transactionsByPrisonerMigrationMappingApiService.createMappings(
//        newUniqueMappings,
//        object : ParameterizedTypeReference<DuplicateErrorResponse<TransactionMappingDto>>() {},
//      ).also {
//        if (it.isError) {
//          val duplicateErrorDetails = (it.errorResponse!!).moreInfo
//          telemetryClient.trackEvent(
//            "nomis-migration-transaction-duplicate",
//            mapOf<String, String>(
//              "offenderNo" to nomsNumber,
//              "duplicateDpsTransactionId" to duplicateErrorDetails.duplicate.dpsTransactionId,
//              "duplicateNomisBookingId" to duplicateErrorDetails.duplicate.nomisBookingId.toString(),
//              "duplicateNomisTransactionId" to duplicateErrorDetails.duplicate.nomisTransactionId.toString(),
//              "existingDpsTransactionId" to duplicateErrorDetails.existing.dpsTransactionId,
//              "existingNomisBookingId" to duplicateErrorDetails.existing.nomisBookingId.toString(),
//              "existingNomisTransactionId" to duplicateErrorDetails.existing.nomisTransactionId.toString(),
//            ),
//            null,
//          )
//        }
//      }
//
//      telemetryClient.trackEvent(
//        "transaction-prisoner-merge",
//        mapOf(
//          "offenderNo" to nomsNumber,
//          "removedOffenderNo" to removedNomsNumber,
//          "bookingId" to bookingId,
//          "newMappingsCount" to newUniqueMappings.size,
//        ),
//      )
//    } catch (e: Exception) {
//      telemetryClient.trackEvent(
//        "transaction-prisoner-merge-failed",
//        mapOf(
//          "offenderNo" to nomsNumber,
//          "removedOffenderNo" to removedNomsNumber,
//          "bookingId" to bookingId,
//          "error" to (e.message ?: "unknown error"),
//        ),
//      )
//      throw e
//    }
//  }
//
//  private fun isMergeTransactionRecentlyCreated(
//    response: TransactionResponse,
//    prisonerMergeEvent: PrisonerMergeDomainEvent,
//  ): Boolean = response.auditModuleName == "MERGE" &&
//    response.createdDatetime.isAfter(prisonerMergeEvent.occurredAt.toLocalDateTime().minusMinutes(30))
//
//  suspend fun synchronisePrisonerBookingMoved(prisonerMergeEvent: PrisonerBookingMovedDomainEvent) {
//    val (movedToNomsNumber, movedFromNomsNumber, bookingId) = prisonerMergeEvent.additionalInformation
//
//    try {
//      val transactions = transactionMappingService.updateMappingsByBookingId(bookingId, movedToNomsNumber)
//      val transactionsToResynchronise = transactions.map { UUID.fromString(it.dpsTransactionId) }.toSet()
//      financeService.moveTransactions(
//        MoveTransactionsRequest(
//          fromPersonIdentifier = movedFromNomsNumber,
//          toPersonIdentifier = movedToNomsNumber,
//          transactionIds = transactionsToResynchronise,
//        ),
//      )
//
//      telemetryClient.trackEvent(
//        "transaction-booking-moved",
//        mapOf(
//          "bookingId" to bookingId,
//          "movedToNomsNumber" to movedToNomsNumber,
//          "movedFromNomsNumber" to movedFromNomsNumber,
//          "count" to transactionsToResynchronise.size,
//        ),
//      )
//    } catch (e: Exception) {
//      telemetryClient.trackEvent(
//        "transaction-booking-moved-failed",
//        mapOf(
//          "bookingId" to bookingId,
//          "movedToNomsNumber" to movedToNomsNumber,
//          "movedFromNomsNumber" to movedFromNomsNumber,
//          "error" to (e.message ?: "unknown error"),
//        ),
//      )
//      throw e
//    }
//  }
//
//  enum class MappingResponse {
//    MAPPING_CREATED,
//    MAPPING_FAILED,
//  }
//
//  suspend fun tryToCreateTransactionMapping(
//    event: TransactionEvent,
//    transactionId: String,
//  ): MappingResponse {
//    val mapping = TransactionMappingDto(
//      dpsTransactionId = transactionId,
//      nomisTransactionId = event.transactionId,
//      nomisBookingId = event.bookingId ?: 0,
//      offenderNo = event.offenderIdDisplay!!,
//      mappingType = TransactionMappingDto.MappingType.NOMIS_CREATED,
//    )
//    try {
//      transactionMappingService.createMapping(
//        mapping,
//        object : ParameterizedTypeReference<DuplicateErrorResponse<TransactionMappingDto>>() {},
//      ).also {
//        if (it.isError) {
//          val duplicateErrorDetails = (it.errorResponse!!).moreInfo
//          telemetryClient.trackEvent(
//            "from-nomis-synch-casenote-duplicate",
//            mapOf(
//              "duplicateDpsTransactionId" to duplicateErrorDetails.duplicate.dpsTransactionId,
//              "duplicateNomisTransactionId" to duplicateErrorDetails.duplicate.nomisTransactionId.toString(),
//              "existingDpsTransactionId" to duplicateErrorDetails.existing.dpsTransactionId,
//              "existingNomisTransactionId" to duplicateErrorDetails.existing.nomisTransactionId.toString(),
//            ),
//          )
//        }
//      }
//      return MappingResponse.MAPPING_CREATED
//    } catch (e: Exception) {
//      log.error(
//        "Failed to create mapping for dpsTransaction id $transactionId, nomisTransactionId ${event.transactionId}",
//        e,
//      )
//      queueService.sendMessage(
//        messageType = RETRY_SYNCHRONISATION_MAPPING.name,
//        synchronisationType = SynchronisationType.FINANCE,
//        message = mapping,
//        telemetryAttributes = event.toTelemetryProperties(transactionId),
//      )
//      return MappingResponse.MAPPING_FAILED
//    }
//  }
//
//  suspend fun retryCreateTransactionMapping(retryMessage: InternalMessage<TransactionMappingDto>) {
//    transactionMappingService.createMapping(
//      retryMessage.body,
//      object : ParameterizedTypeReference<DuplicateErrorResponse<TransactionMappingDto>>() {},
//    ).also {
//      telemetryClient.trackEvent(
//        "transaction-mapping-created-synchronisation-success",
//        retryMessage.telemetryAttributes,
//      )
//    }
//  }
}

private fun TransactionEvent.toTelemetryProperties(
  dpsTransactionId: String? = null,
  mappingFailed: Boolean? = null,
) = mapOf(
  "nomisTransactionId" to this.transactionId.toString(),
  "offenderNo" to this.offenderIdDisplay.toString(),
  "bookingId" to this.bookingId.toString(),
) + (dpsTransactionId?.let { mapOf("dpsTransactionId" to it) } ?: emptyMap()) + (
  if (mappingFailed == true) mapOf("mapping" to "initial-failure") else emptyMap()
  )

private fun TransactionEvent.isSourcedFromDPS() = auditModuleName == "DPS_SYNCHRONISATION"
private fun TransactionEvent.auditMissing() = auditModuleName == null

// private fun isMergeCopy(
//  response: TransactionResponse,
//  mergeCopiedTransaction: TransactionResponse,
// ): Boolean = response.creationDateTime == mergeCopiedTransaction.creationDateTime &&
//  response.transactionText == mergeCopiedTransaction.transactionText &&
//  response.bookingId != mergeCopiedTransaction.bookingId &&
//  response.auditModuleName != "MERGE" &&
//  response.transactionType == mergeCopiedTransaction.transactionType &&
//  response.transactionSubType == mergeCopiedTransaction.transactionSubType &&
//  response.occurrenceDateTime == mergeCopiedTransaction.occurrenceDateTime &&
//  response.authorStaffId == mergeCopiedTransaction.authorStaffId &&
//  response.amendments.size == mergeCopiedTransaction.amendments.size
