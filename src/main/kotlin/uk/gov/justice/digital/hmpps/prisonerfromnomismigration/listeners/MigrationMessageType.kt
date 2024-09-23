package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners

enum class MigrationMessageType {
  MIGRATE_ENTITIES,
  MIGRATE_BY_PAGE,
  MIGRATE_ENTITY,
  MIGRATE_STATUS_CHECK, // status check and cancel work at queue level. The queue is used by all Sentencing migration entity migrations
  CANCEL_MIGRATION,
  RETRY_MIGRATION_MAPPING,
}

enum class SynchronisationMessageType {
  RETRY_SYNCHRONISATION_MAPPING,
  RETRY_SYNCHRONISATION_MAPPING_CHILD,
  RETRY_SYNCHRONISATION_MAPPING_BATCH,
  RETRY_RESYNCHRONISATION_MAPPING_BATCH,
  RETRY_RESYNCHRONISATION_MERGED_MAPPING_BATCH,
  RESYNCHRONISE_MOVE_BOOKING_TARGET,
}
