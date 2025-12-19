package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.appointments

import com.fasterxml.jackson.databind.ObjectMapper
import io.awspring.cloud.sqs.annotation.SqsListener
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.annotations.WithSpan
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.sqs.model.Message
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageListener
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.APPOINTMENTS_QUEUE_ID
import java.util.concurrent.CompletableFuture

@Service
class AppointmentsMigrationMessageListener(
  objectMapper: ObjectMapper,
  appointmentsMigrationService: AppointmentsMigrationService,
) : MigrationMessageListener(
  objectMapper,
  appointmentsMigrationService,
) {

  @SqsListener(APPOINTMENTS_QUEUE_ID, factory = "hmppsQueueContainerFactoryProxy")
  @WithSpan(value = "dps-syscon-migration_appointments_queue", kind = SpanKind.SERVER)
  fun onAppointmentsMessage(message: String, rawMessage: Message): CompletableFuture<Void?> = onMessage(message, rawMessage)
}
