package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners

enum class MessageType {
  MIGRATE_ENTITIES,
  MIGRATE_BY_PAGE,
  MIGRATE_ENTITY,
  MIGRATE_STATUS_CHECK, // status check and cancel work at queue level. The queue is used by all Sentencing migration entity migrations
  CANCEL_MIGRATION,
  RETRY_MIGRATION_MAPPING,
  RETRY_SYNCHRONISATION_MAPPING,
}
