package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance

import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.model.GeneralLedgerEntry
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.model.OffenderTransaction
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.model.SyncGeneralLedgerTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.model.SyncOffenderTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.GeneralLedgerTransactionDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderTransactionDto
import java.util.*

fun List<OffenderTransactionDto>.toSyncOffenderTransactionRequest(requestId: UUID): SyncOffenderTransactionRequest = first()
  .run {
    SyncOffenderTransactionRequest(
      transactionId = transactionId,
      requestId = requestId,
      caseloadId = caseloadId,
      transactionTimestamp = generalLedgerTransactions.firstOrNull()?.transactionTimestamp
        ?: createdAt,
      createdAt = createdAt,
      createdBy = createdBy,
      createdByDisplayName = createdByDisplayName,
      lastModifiedAt = lastModifiedAt,
      lastModifiedBy = lastModifiedBy,
      lastModifiedByDisplayName = lastModifiedByDisplayName,
      offenderTransactions = this@toSyncOffenderTransactionRequest.map {
        it.toDPSSyncTransaction()
      },
    )
  }

fun List<GeneralLedgerTransactionDto>.toSyncGeneralLedgerTransactionRequest(requestId: UUID): SyncGeneralLedgerTransactionRequest = first()
  .run {
    SyncGeneralLedgerTransactionRequest(
      transactionId = transactionId,
      requestId = requestId,
      caseloadId = caseloadId,
      transactionType = type,
      reference = reference,
      transactionTimestamp = transactionTimestamp,
      description = description,
      createdAt = createdAt,
      createdBy = createdBy,
      createdByDisplayName = createdByDisplayName,
      lastModifiedAt = lastModifiedAt,
      lastModifiedBy = lastModifiedBy,
      lastModifiedByDisplayName = lastModifiedByDisplayName,
      generalLedgerEntries = this@toSyncGeneralLedgerTransactionRequest.map {
        it.toDPSSyncGLTransaction()
      },
    )
  }

fun OffenderTransactionDto.toDPSSyncTransaction(): OffenderTransaction = OffenderTransaction(
  entrySequence = transactionEntrySequence,
  offenderId = offenderId,
  offenderDisplayId = offenderNo,
  subAccountType = subAccountType.name,
  postingType = OffenderTransaction.PostingType.valueOf(postingType.name),
  type = type,
  description = description,
  amount = amount,
  offenderBookingId = bookingId,
  reference = reference,
  generalLedgerEntries = this.generalLedgerTransactions.map {
    it.toDPSSyncGLTransaction()
  },
)

fun GeneralLedgerTransactionDto.toDPSSyncGLTransaction() = GeneralLedgerEntry(
  entrySequence = generalLedgerEntrySequence,
  code = accountCode,
  postingType = GeneralLedgerEntry.PostingType.valueOf(postingType.name),
  amount = amount,
)
