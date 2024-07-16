package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageImpl
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PrisonPersonMigrationMappingRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.PhysicalAttributesResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.PrisonerId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.PrisonerPhysicalAttributesResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.AuditService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationHistoryService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService
import java.time.LocalDateTime

@Service
class PrisonPersonMigrationService(
  queueService: MigrationQueueService,
  private val prisonPersonNomisApiService: PrisonPersonNomisApiService,
  private val nomisService: NomisApiService,
  private val prisonPersonMappingService: PrisonPersonMappingApiService,
  private val prisonPersonDpsService: PrisonPersonDpsApiService,
  migrationHistoryService: MigrationHistoryService,
  telemetryClient: TelemetryClient,
  auditService: AuditService,
  @Value("\${page.size:1000}") pageSize: Long,
  @Value("\${complete-check.delay-seconds}") completeCheckDelaySeconds: Int,
  @Value("\${complete-check.count}") completeCheckCount: Int,
) : MigrationService<PrisonPersonMigrationFilter, PrisonerId, PrisonerPhysicalAttributesResponse, PrisonPersonMigrationMappingRequest>(
  queueService = queueService,
  auditService = auditService,
  migrationHistoryService = migrationHistoryService,
  mappingService = prisonPersonMappingService,
  telemetryClient = telemetryClient,
  migrationType = MigrationType.PRISONPERSON,
  pageSize = pageSize,
  completeCheckDelaySeconds = completeCheckDelaySeconds,
  completeCheckCount = completeCheckCount,
) {
  override suspend fun getIds(
    migrationFilter: PrisonPersonMigrationFilter,
    pageSize: Long,
    pageNumber: Long,
  ): PageImpl<PrisonerId> = nomisService.getPrisonerIds(
    pageNumber = pageNumber,
    pageSize = pageSize,
  )

  override suspend fun migrateNomisEntity(context: MigrationContext<PrisonerId>) {
    log.info("attempting to migrate ${context.body}")
    val offenderNo = context.body.offenderNo

    val physicalAttributes = prisonPersonNomisApiService.getPhysicalAttributes(offenderNo)
    lateinit var dpsIds: List<Long>
    physicalAttributes.bookings.flatMap { booking ->
      booking.physicalAttributes.map { pa ->
        val (lastModifiedAt, lastModifiedBy) = pa.lastModified()
        prisonPersonDpsService.migratePhysicalAttributesRequest(
          heightCentimetres = pa.heightCentimetres,
          weightKilograms = pa.weightKilograms,
          appliesFrom = booking.startDateTime.toLocalDateTime(),
          appliesTo = booking.endDateTime?.toLocalDateTime(),
          createdAt = lastModifiedAt,
          createdBy = lastModifiedBy,
        )
      }
    }.also { requests ->
      prisonPersonDpsService.migratePhysicalAttributes(offenderNo, requests)
        .also { response ->
          dpsIds = response.fieldHistoryInserted
        }
    }
    // TODO create mappings
    telemetryClient.trackEvent(
      "prisonperson-migration-entity-migrated",
      mapOf(
        "offenderNo" to offenderNo,
        "migrationId" to context.migrationId,
        "dpsIds" to dpsIds,
      ),
    )
  }

  private fun PhysicalAttributesResponse.lastModified(): Pair<LocalDateTime, String> =
    (modifiedDateTime ?: createDateTime).toLocalDateTime() to (modifiedBy ?: createdBy)

  private fun String.toLocalDateTime() = LocalDateTime.parse(this)

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
