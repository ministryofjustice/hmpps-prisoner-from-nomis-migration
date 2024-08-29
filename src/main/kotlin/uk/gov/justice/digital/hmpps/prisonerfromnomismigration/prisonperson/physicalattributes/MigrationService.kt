package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.physicalattributes

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.PrisonerPhysicalAttributesResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.PrisonPersonEntityMigrator
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.PrisonPersonMappingApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.PrisonPersonMigrationService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.model.PhysicalAttributesMigrationRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.AuditService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationHistoryService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService

@Service
class MigrationService(
  queueService: MigrationQueueService,
  nomisService: NomisApiService,
  prisonPersonMappingService: PrisonPersonMappingApiService,
  migrationHistoryService: MigrationHistoryService,
  telemetryClient: TelemetryClient,
  auditService: AuditService,
  @Value("\${page.size:1000}") pageSize: Long,
  @Value("\${complete-check.delay-seconds}") completeCheckDelaySeconds: Int,
  @Value("\${complete-check.count}") completeCheckCount: Int,
  @Qualifier("physicalAttributesEntityMigrator")
  private val entityMigrator: PrisonPersonEntityMigrator<PrisonerPhysicalAttributesResponse, PhysicalAttributesMigrationRequest>,
) : PrisonPersonMigrationService(
  queueService,
  nomisService,
  prisonPersonMappingService,
  migrationHistoryService,
  telemetryClient,
  auditService,
  pageSize,
  completeCheckDelaySeconds,
  completeCheckCount,
) {
  override suspend fun migrateEntity(offenderNo: String): List<Long> =
    entityMigrator.migrateEntity(offenderNo)
}
