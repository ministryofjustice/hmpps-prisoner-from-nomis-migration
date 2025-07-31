package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements

import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderTemporaryAbsencesResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService

@Service
class ExternalMovementsMigrationService(
  val migrationMappingService: ExternalMovementsMappingApiService,
  val nomisIdsApiService: NomisApiService,
  val externalMovementsNomisApiService: ExternalMovementsNomisApiService,
  @Value($$"${externalmovements.page.size:1000}") pageSize: Long,
  @Value($$"${externalmovements.complete-check.delay-seconds}") completeCheckDelaySeconds: Int,
  @Value($$"${externalmovements.complete-check.retry-seconds:1}") completeCheckRetrySeconds: Int,
  @Value($$"${externalmovements.complete-check.count}") completeCheckCount: Int,
  @Value($$"${complete-check.scheduled-retry-seconds:10}") completeCheckScheduledRetrySeconds: Int,
) : MigrationService<ExternalMovementsMigrationFilter, PrisonerId, TemporaryAbsencesPrisonerMappingDto>(
  mappingService = migrationMappingService,
  migrationType = MigrationType.EXTERNAL_MOVEMENTS,
  pageSize = pageSize,
  completeCheckDelaySeconds = completeCheckDelaySeconds,
  completeCheckCount = completeCheckCount,
  completeCheckRetrySeconds = completeCheckRetrySeconds,
  completeCheckScheduledRetrySeconds = completeCheckScheduledRetrySeconds,
) {
  override suspend fun getIds(
    migrationFilter: ExternalMovementsMigrationFilter,
    pageSize: Long,
    pageNumber: Long,
  ): PageImpl<PrisonerId> = if (migrationFilter.prisonerNumber.isNullOrEmpty()) {
    nomisIdsApiService.getPrisonerIds(
      pageNumber = pageNumber,
      pageSize = pageSize,
    )
  } else {
    // If a single prisoner migration is requested, then we'll trust the input as we're probably testing. Pretend that we called nomis-prisoner-api which found a single prisoner.
    PageImpl<PrisonerId>(mutableListOf(PrisonerId(migrationFilter.prisonerNumber)), Pageable.ofSize(1), 1)
  }

  override suspend fun migrateNomisEntity(context: MigrationContext<PrisonerId>) {
    val offenderNo = context.body.offenderNo
    val migrationId = context.migrationId
    val telemetry = mutableMapOf(
      "offenderNo" to offenderNo,
      "migrationId" to migrationId,
    )

    migrationMappingService.getPrisonerTemporaryAbsenceMappings(offenderNo)
      ?.run { publishTelemetry("ignored", telemetry.apply { this["reason"] = "Already migrated" }) }
      ?: run {
        val temporaryAbsences = externalMovementsNomisApiService.getTemporaryAbsences(offenderNo)

        // TODO SDIT-2846 call the DPS endpoint to migrate and use the returned IDs in the mappings
        val mappings = temporaryAbsences.buildMappings(offenderNo, migrationId)

        createMappingOrOnFailureDo(mappings) {
          requeueCreateMapping(mappings, context)
        }
      }
  }

  override suspend fun retryCreateMapping(context: MigrationContext<TemporaryAbsencesPrisonerMappingDto>) {
    createMappingOrOnFailureDo(context.body) {
      throw it
    }
  }

  private suspend fun createMappingOrOnFailureDo(
    mapping: TemporaryAbsencesPrisonerMappingDto,
    failureHandler: suspend (error: Throwable) -> Unit,
  ) {
    runCatching {
      createMapping(mapping)
    }.onSuccess {
      publishTelemetry(
        if (it.isError) "duplicate" else "migrated",
        mapOf(
          "offenderNo" to mapping.prisonerNumber,
          "migrationId" to mapping.migrationId,
        ),
      )
    }.onFailure {
      failureHandler(it)
    }
  }

  private suspend fun createMapping(mapping: TemporaryAbsencesPrisonerMappingDto) = migrationMappingService.createMapping(
    mapping,
    object :
      ParameterizedTypeReference<DuplicateErrorResponse<TemporaryAbsencesPrisonerMappingDto>>() {},
  )

  private suspend fun requeueCreateMapping(
    mapping: TemporaryAbsencesPrisonerMappingDto,
    context: MigrationContext<*>,
  ) {
    queueService.sendMessage(
      MigrationMessageType.RETRY_MIGRATION_MAPPING,
      MigrationContext(
        context = context,
        body = mapping,
      ),
    )
  }

  private fun publishTelemetry(type: String, telemetry: Map<String, String>) {
    telemetryClient.trackEvent(
      "temporary-absences-migration-entity-$type",
      telemetry,
    )
  }

  private fun OffenderTemporaryAbsencesResponse.buildMappings(prisonerNumber: String, migrationId: String) = TemporaryAbsencesPrisonerMappingDto(
    prisonerNumber = prisonerNumber,
    migrationId = migrationId,
    bookings = bookings.map { booking ->
      TemporaryAbsenceBookingMappingDto(
        bookingId = booking.bookingId,
        applications = booking.temporaryAbsenceApplications.map { application ->
          TemporaryAbsenceApplicationMappingDto(
            nomisMovementApplicationId = application.movementApplicationId,
            dpsMovementApplicationId = application.movementApplicationId + 1000,
            outsideMovements = application.outsideMovements.map { outside ->
              TemporaryAbsencesOutsideMovementMappingDto(
                nomisMovementApplicationMultiId = outside.outsideMovementId,
                dpsOutsideMovementId = outside.outsideMovementId + 1000,
              )
            },
            scheduledAbsence = application.scheduledTemporaryAbsence?.let { sta ->
              ScheduledMovementMappingDto(
                nomisEventId = sta.eventId,
                dpsScheduledMovementId = sta.eventId + 1000,
              )
            },
            scheduledAbsenceReturn = application.scheduledTemporaryAbsenceReturn?.let { star ->
              ScheduledMovementMappingDto(
                nomisEventId = star.eventId,
                dpsScheduledMovementId = star.eventId + 1000,
              )
            },
            absence = application.temporaryAbsence?.let { ta ->
              ExternalMovementMappingDto(
                nomisMovementSeq = ta.sequence,
                dpsExternalMovementId = ta.sequence + 1000,
              )
            },
            absenceReturn = application.temporaryAbsenceReturn?.let { tar ->
              ExternalMovementMappingDto(
                nomisMovementSeq = tar.sequence,
                dpsExternalMovementId = tar.sequence + 1000,
              )
            },
          )
        },
        unscheduledMovements = booking.unscheduledTemporaryAbsences.map { uta ->
          ExternalMovementMappingDto(
            nomisMovementSeq = uta.sequence,
            dpsExternalMovementId = uta.sequence + 1000,
          )
        } + booking.unscheduledTemporaryAbsenceReturns.map { utar ->
          ExternalMovementMappingDto(
            nomisMovementSeq = utar.sequence,
            dpsExternalMovementId = utar.sequence + 1000,
          )
        },
      )
    },
  )
}
