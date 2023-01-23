package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing

enum class SentencingMessages {
  MIGRATE_SENTENCE_ADJUSTMENTS,
  MIGRATE_SENTENCING_ADJUSTMENTS_BY_PAGE,
  MIGRATE_SENTENCING_ADJUSTMENT,
  MIGRATE_SENTENCING_STATUS_CHECK, // status check and cancel work at queue level. The queue is used by all Sentencing migration entity migrations
  CANCEL_MIGRATE_SENTENCING,
  RETRY_SENTENCING_ADJUSTMENT_MAPPING,
}
