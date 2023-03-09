package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incentives

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageImpl
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incentives.ReviewType.MIGRATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.AuditService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.IncentiveId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationHistoryService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType.INCENTIVES
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisIncentive
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.asMap
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.durationMinutes

@Service
class IncentivesMigrationService(
  private val queueService: MigrationQueueService,
  private val nomisApiService: NomisApiService,
  migrationHistoryService: MigrationHistoryService,
  private val telemetryClient: TelemetryClient,
  auditService: AuditService,
  private val incentivesService: IncentivesService,
  private val incentiveMappingService: IncentiveMappingService,
  @Value("\${incentives.page.size:1000}") private val pageSize: Long,
) : MigrationService<IncentivesMigrationFilter, IncentiveId, NomisIncentive, IncentiveNomisMapping>(
  queueService = queueService,
  auditService = auditService,
  migrationHistoryService = migrationHistoryService,
  mappingService = incentiveMappingService,
  telemetryClient = telemetryClient,
  migrationType = INCENTIVES,
  pageSize = pageSize,
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  override suspend fun getIds(
    migrationFilter: IncentivesMigrationFilter,
    pageSize: Long,
    pageNumber: Long,
  ): PageImpl<IncentiveId> {
    return nomisApiService.getIncentives(
      fromDate = migrationFilter.fromDate,
      toDate = migrationFilter.toDate,
      pageNumber = pageNumber,
      pageSize = pageSize,
    )
  }

  suspend fun migrateIncentive(context: MigrationContext<IncentiveId>) {
    val (bookingId, sequence) = context.body

    incentiveMappingService.findNomisIncentiveMapping(bookingId, sequence)?.run {
      log.info("Will not migrate incentive since it is migrated already, Booking id is ${context.body.bookingId}, sequence ${context.body.sequence}, incentive id is ${this.incentiveId} as part migration ${this.label ?: "NONE"} (${this.mappingType})")
    }
      ?: run {
        val iep = nomisApiService.getIncentive(bookingId, sequence)
        val migratedIncentive = incentivesService.migrateIncentive(iep.toIncentive(reviewType = MIGRATED), bookingId)
          .also {
            createIncentiveMapping(
              bookingId,
              nomisIncentiveSequence = sequence,
              incentiveId = it.id,
              context = context,
            )
          }
        telemetryClient.trackEvent(
          "incentives-migration-entity-migrated",
          mapOf(
            "migrationId" to context.migrationId,
            "bookingId" to bookingId.toString(),
            "sequence" to sequence.toString(),
            "incentiveId" to migratedIncentive.id.toString(),
            "level" to iep.iepLevel.code,
          ),
          null,
        )
      }
  }

  override suspend fun migrateNomisEntity(context: MigrationContext<IncentiveId>) {
    log.info("attempting to migrate ${context.body}")
    val nomisBookingId = context.body.bookingId
    val nomisSequence = context.body.sequence

    incentiveMappingService.findNomisIncentiveMapping(nomisBookingId, nomisSequence)?.run {
      log.info("Will not migrate the iep since it is migrated already, NOMIS booking id is $nomisBookingId, nomisSequence is $nomisSequence, incentive id is ${this.incentiveId} as part of migration ${this.label ?: "NONE"} (${this.mappingType})")
    }
      ?: run {
        val nomisAdjustment =
          nomisApiService.getIncentive(nomisBookingId, nomisSequence)

        val incentiveIEPResponse =
          incentivesService.migrateIncentive(nomisAdjustment.toIncentive(MIGRATED), nomisBookingId)
            .also {
              createIncentiveMapping(
                bookingId = nomisBookingId,
                nomisIncentiveSequence = nomisSequence,
                incentiveId = it.id,
                context = context,
              )
            }
        telemetryClient.trackEvent(
          "nomis-migration-incentive-migrated",
          mapOf(
            "incentiveId" to incentiveIEPResponse.id.toString(),
            "migrationId" to context.migrationId,
          ) + nomisAdjustment.asMap(),
          null,
        )
      }
  }

  private suspend fun createIncentiveMapping(
    bookingId: Long,
    nomisIncentiveSequence: Long,
    incentiveId: Long,
    context: MigrationContext<*>,
  ) = try {
    incentiveMappingService.createMapping(
      IncentiveNomisMapping(
        nomisBookingId = bookingId,
        nomisIncentiveSequence = nomisIncentiveSequence,
        incentiveId = incentiveId,
        label = context.migrationId,
        mappingType = "MIGRATED",
      ),
    ).also {
      if (it.isError) {
        val duplicateErrorDetails = (it.errorResponse as DuplicateIncentiveErrorResponse).moreInfo
        telemetryClient.trackEvent(
          "nomis-migration-incentive-duplicate",
          mapOf<String, String>(
            "migrationId" to context.migrationId,
            "duplicateIncentiveId" to duplicateErrorDetails.duplicateIncentive.incentiveId.toString(),
            "duplicateNomisBookingId" to duplicateErrorDetails.duplicateIncentive.nomisBookingId.toString(),
            "duplicateNomisSequence" to duplicateErrorDetails.duplicateIncentive.nomisIncentiveSequence.toString(),
            "existingIncentiveId" to duplicateErrorDetails.existingIncentive.incentiveId.toString(),
            "existingNomisBookingId" to duplicateErrorDetails.existingIncentive.nomisBookingId.toString(),
            "existingNomisSequence" to duplicateErrorDetails.existingIncentive.nomisIncentiveSequence.toString(),
            "durationMinutes" to context.durationMinutes().toString(),
          ),
          null,
        )
      }
    }
  } catch (e: Exception) {
    log.error(
      "Failed to create mapping for incentive $bookingId, sequence $nomisIncentiveSequence, Incentive id $incentiveId",
      e,
    )
    queueService.sendMessage(
      MigrationMessageType.RETRY_MIGRATION_MAPPING,
      MigrationContext(
        context = context,
        body = IncentiveNomisMapping(
          nomisBookingId = bookingId,
          nomisIncentiveSequence = nomisIncentiveSequence,
          incentiveId = incentiveId,
          mappingType = "MIGRATED",
        ),
      ),
    )
  }
}
