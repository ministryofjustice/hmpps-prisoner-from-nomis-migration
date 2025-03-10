package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.appointments

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.data.domain.PageImpl
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.AppointmentIdResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType.APPOINTMENTS
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.durationMinutes

@Service
class AppointmentsMigrationService(
  private val nomisApiService: NomisApiService,
  private val appointmentsService: AppointmentsService,
  private val appointmentsMappingService: AppointmentsMappingService,
  @Value("\${appointments.page.size:1000}") pageSize: Long,
  @Value("\${complete-check.delay-seconds}") completeCheckDelaySeconds: Int,
  @Value("\${complete-check.count}") completeCheckCount: Int,
) : MigrationService<AppointmentsMigrationFilter, AppointmentIdResponse, AppointmentMapping>(
  mappingService = appointmentsMappingService,
  migrationType = APPOINTMENTS,
  pageSize = pageSize,
  completeCheckDelaySeconds = completeCheckDelaySeconds,
  completeCheckCount = completeCheckCount,
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  override suspend fun getIds(
    migrationFilter: AppointmentsMigrationFilter,
    pageSize: Long,
    pageNumber: Long,
  ): PageImpl<AppointmentIdResponse> = nomisApiService.getAppointmentIds(
    fromDate = migrationFilter.fromDate,
    toDate = migrationFilter.toDate,
    pageNumber = pageNumber,
    pageSize = pageSize,
    prisonIds = migrationFilter.prisonIds,
  )

  override suspend fun migrateNomisEntity(context: MigrationContext<AppointmentIdResponse>) {
    log.info("attempting to migrate ${context.body}")
    val eventId = context.body.eventId

    appointmentsMappingService.findNomisMapping(eventId)
      ?.run {
        log.info("Will not migrate the appointment since it is migrated already, NOMIS event id is $eventId, appointmentInstanceId is ${this.appointmentInstanceId} as part migration ${this.label ?: "NONE"} (${this.mappingType})")
      }
      ?: run {
        val appointment = nomisApiService.getAppointment(eventId)

        appointmentsService.createAppointment(appointment.toAppointment())
          ?.also {
            createAppointmentMapping(
              nomisEventId = eventId,
              appointmentInstanceId = it.id,
              context = context,
            )

            telemetryClient.trackEvent(
              "appointments-migration-entity-migrated",
              mapOf(
                "nomisEventId" to eventId.toString(),
                "appointmentInstanceId" to it.id.toString(),
                "migrationId" to context.migrationId,
              ),
              null,
            )
          }
          ?: run {
            telemetryClient.trackEvent(
              "appointments-migration-entity-ignored",
              mapOf(
                "nomisEventId" to eventId.toString(),
                "reason" to "DPS returned null indicating they ignored the appointment",
                "migrationId" to context.migrationId,
              ),
              null,
            )
          }
      }
  }

  private suspend fun createAppointmentMapping(
    nomisEventId: Long,
    appointmentInstanceId: Long,
    context: MigrationContext<*>,
  ) = try {
    appointmentsMappingService.createMapping(
      AppointmentMapping(
        nomisEventId = nomisEventId,
        appointmentInstanceId = appointmentInstanceId,
        label = context.migrationId,
        mappingType = "MIGRATED",
      ),
      object : ParameterizedTypeReference<DuplicateErrorResponse<AppointmentMapping>>() {},
    ).also {
      if (it.isError) {
        val duplicateErrorDetails = (it.errorResponse!!).moreInfo
        telemetryClient.trackEvent(
          "nomis-migration-appointment-duplicate",
          mapOf<String, String>(
            "migrationId" to context.migrationId,
            "duplicateAppointmentInstanceId" to duplicateErrorDetails.duplicate.appointmentInstanceId.toString(),
            "duplicateNomisEventId" to duplicateErrorDetails.duplicate.nomisEventId.toString(),
            "existingAppointmentInstanceId" to duplicateErrorDetails.existing.appointmentInstanceId.toString(),
            "existingNomisEventId" to duplicateErrorDetails.existing.nomisEventId.toString(),
            "durationMinutes" to context.durationMinutes().toString(),
          ),
          null,
        )
      }
    }
  } catch (e: Exception) {
    log.error(
      "Failed to create mapping for nomisEventId: $nomisEventId, appointmentInstanceId $appointmentInstanceId",
      e,
    )
    queueService.sendMessage(
      MigrationMessageType.RETRY_MIGRATION_MAPPING,
      MigrationContext(
        context = context,
        body = AppointmentMapping(
          nomisEventId = nomisEventId,
          appointmentInstanceId = appointmentInstanceId,
          mappingType = "MIGRATED",
        ),
      ),
    )
  }
}
