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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.asStringOrBlank

@Service
class IncentivesMigrationService(
  private val queueService: MigrationQueueService,
  private val nomisApiService: NomisApiService,
  migrationHistoryService: MigrationHistoryService,
  private val telemetryClient: TelemetryClient,
  auditService: AuditService,
  private val incentivesService: IncentivesService,
  private val incentiveMappingService: IncentiveMappingService,
  @Value("\${sentencing.page.size:1000}") private val pageSize: Long,
) : MigrationService<IncentivesMigrationFilter, IncentiveId, NomisIncentive, IncentiveNomisMapping>(
  queueService = queueService,
  auditService = auditService,
  migrationHistoryService = migrationHistoryService,
  telemetryClient = telemetryClient,
  migrationType = INCENTIVES,
  pageSize = pageSize,
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  override suspend fun getIds(migrationFilter: IncentivesMigrationFilter, pageSize: Long, pageNumber: Long): PageImpl<IncentiveId> {
    return nomisApiService.getIncentives(
      fromDate = migrationFilter.fromDate,
      toDate = migrationFilter.toDate,
      pageNumber = pageNumber,
      pageSize = pageSize,
    )
  }

  override fun getTelemetryFromFilter(migrationFilter: IncentivesMigrationFilter): Map<String, String> {
    return mapOf(
      "migrationType" to "Incentives",
      "fromDate" to migrationFilter.fromDate.asStringOrBlank(),
      "toDate" to migrationFilter.toDate.asStringOrBlank(),
    )
  }

  override fun getTelemetryFromNomisEntity(nomisEntity: NomisIncentive): Map<String, String> {
    return mapOf(
      "migrationType" to "Incentives",
      "nomisSequence" to nomisEntity.incentiveSequence.toString(),
      "bookingId" to nomisEntity.bookingId.toString(),
      "sequence" to nomisEntity.incentiveSequence.toString(),
      "level" to nomisEntity.iepLevel.code,
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
          "nomis-migration-incentive-migrated",
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

  override fun getMigrationType() = INCENTIVES

  override suspend fun getMigrationCount(migrationId: String): Long {
    return incentiveMappingService.getMigrationCount(migrationId)
  }

  override suspend fun retryCreateMapping(context: MigrationContext<IncentiveNomisMapping>) {
    incentiveMappingService.createNomisIncentiveMigrationMapping(
      nomisBookingId = context.body.nomisBookingId,
      nomisIncentiveSequence = context.body.nomisIncentiveSequence,
      incentiveId = context.body.incentiveId,
      migrationId = context.migrationId,
    )
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
          ) + getTelemetryFromNomisEntity(nomisAdjustment),
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
    incentiveMappingService.createNomisIncentiveMigrationMapping(
      nomisBookingId = bookingId,
      nomisIncentiveSequence = nomisIncentiveSequence,
      incentiveId = incentiveId,
      migrationId = context.migrationId,
    )
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
