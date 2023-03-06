package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.SynchronisationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationMessage

inline fun <reified T> migrationContext(message: MigrationMessage<*, T>): MigrationContext<T> =
  message.context

inline fun <reified T> synchronisationContext(message: SynchronisationMessage<*, T>): SynchronisationContext<T> =
  message.context

@JsonNaming(value = PropertyNamingStrategies.UpperCamelCaseStrategy::class)
data class SQSMessage(val Type: String, val Message: String, val MessageId: String? = null, val MessageAttributes: MessageAttributes? = null)
data class MessageAttributes(val eventType: EventType)
data class EventType(val Value: String, val Type: String)
