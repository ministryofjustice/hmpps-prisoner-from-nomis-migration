@file:Suppress("PropertyName")

package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import io.opentelemetry.context.Context
import io.opentelemetry.extension.kotlin.asContextElement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.future.future
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationMessage
import java.util.concurrent.CompletableFuture

inline fun <reified T> migrationContext(message: MigrationMessage<*, T>): MigrationContext<T> = message.context

@JsonNaming(value = PropertyNamingStrategies.UpperCamelCaseStrategy::class)
@JsonInclude(NON_NULL)
data class SQSMessage(val Type: String, val Message: String, val MessageId: String? = null, val MessageAttributes: MessageAttributes? = null)
data class MessageAttributes(val eventType: EventType)
data class EventType(val Value: String, val Type: String)

fun asCompletableFuture(process: suspend () -> Unit): CompletableFuture<Void?> = CoroutineScope(Context.current().asContextElement()).future {
  process()
  null
}
