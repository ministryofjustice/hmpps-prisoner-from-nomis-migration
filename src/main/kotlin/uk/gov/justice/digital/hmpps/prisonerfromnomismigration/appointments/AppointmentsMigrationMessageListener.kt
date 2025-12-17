package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.appointments

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.awspring.cloud.sqs.annotation.SqsListener
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.annotations.WithSpan
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.sqs.model.Message
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageListener
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.APPOINTMENTS_QUEUE_ID
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationPage
import java.util.concurrent.CompletableFuture

@Service
class AppointmentsMigrationMessageListener(
  objectMapper: ObjectMapper,
  appointmentsMigrationService: AppointmentsMigrationService,
) : MigrationMessageListener<AppointmentsMigrationFilter, AppointmentIdResponse, AppointmentResponse, AppointmentMapping>(
  objectMapper,
  appointmentsMigrationService,
) {

  @SqsListener(APPOINTMENTS_QUEUE_ID, factory = "hmppsQueueContainerFactoryProxy")
  @WithSpan(value = "dps-syscon-migration_appointments_queue", kind = SpanKind.SERVER)
  fun onAppointmentsMessage(message: String, rawMessage: Message): CompletableFuture<Void?> = onMessage(message, rawMessage)

  override fun parseContextFilter(json: String): MigrationMessage<*, AppointmentsMigrationFilter> = objectMapper.readValue(json)

  override fun parseContextPageFilter(json: String): MigrationMessage<*, MigrationPage<AppointmentsMigrationFilter, AppointmentIdResponse>> = objectMapper.readValue(json)

  override fun parseContextNomisId(json: String): MigrationMessage<*, AppointmentIdResponse> = objectMapper.readValue(json)

  override fun parseContextMapping(json: String): MigrationMessage<*, AppointmentMapping> = objectMapper.readValue(json)
}
