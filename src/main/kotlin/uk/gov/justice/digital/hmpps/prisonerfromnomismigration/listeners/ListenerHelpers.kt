package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners

import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationMessage

inline fun <reified T> context(message: MigrationMessage<*, T>): MigrationContext<T> =
  message.context
