package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners

import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationMessage

inline fun <reified T> context(message: MigrationMessage<*, T>): MigrationContext<T> =
  message.context

data class SQSMessage(val Message: String, val MessageId: String, val MessageAttributes: MessageAttributes)
data class MessageAttributes(val eventType: EventType)
data class EventType(val Value: String, val Type: String)
