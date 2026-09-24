package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance

import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.model.GeneralLedgerEntry
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.model.OffenderTransaction
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.model.SyncCreateHoldRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.model.SyncOffenderTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.model.SyncReleaseHoldRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.GeneralLedgerTransactionDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderTransactionDto
import java.util.UUID

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

fun OffenderTransactionDto.toSyncAddHoldRequest(): SyncCreateHoldRequest = SyncCreateHoldRequest(
  prisonNumber = offenderNo,
  subAccountCode = subAccountType.toAccountCode(),
  holdNumber = holdDetails!!.holdNumber,
  holdUntilDate = holdDetails.holdUntilDate?.atStartOfDay(),
  isReleased = holdDetails.holdCleared,
  holdTransactionId = transactionId,
  holdFromDate = generalLedgerTransactions.firstOrNull()?.transactionTimestamp ?: createdAt,
  holdType = type,
  holdLocation = caseloadId,
  amount = amount,
  description = description,
  createdAt = createdAt,
  createdBy = createdBy,
)

fun OffenderTransactionDto.toSyncReleaseHoldRequest(): SyncReleaseHoldRequest = SyncReleaseHoldRequest(releaseDateTime = generalLedgerTransactions.firstOrNull()?.transactionTimestamp ?: createdAt)

fun OffenderTransactionDto.SubAccountType.toAccountCode() = when (this) {
  OffenderTransactionDto.SubAccountType.REG -> 2101
  OffenderTransactionDto.SubAccountType.SPND -> 2102
  OffenderTransactionDto.SubAccountType.SAV -> 2103
  else -> throw IllegalArgumentException("Unexpected sub account type: $this")
}
