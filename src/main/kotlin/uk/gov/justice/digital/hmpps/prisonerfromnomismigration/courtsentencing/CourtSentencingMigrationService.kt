package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageImpl
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.CreateCourtCaseResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtCaseAllMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CourtCaseIdResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CourtCaseResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.AuditService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationHistoryService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType

@Service
class CourtSentencingMigrationService(
  queueService: MigrationQueueService,
  private val nomisApiService: CourtSentencingNomisApiService,
  migrationHistoryService: MigrationHistoryService,
  telemetryClient: TelemetryClient,
  auditService: AuditService,
  private val courtSentencingMappingService: CourtSentencingMappingApiService,
  private val courtSentencingDpsService: CourtSentencingDpsApiService,
  @Value("\${court-case.page.size:1000}") pageSize: Long,
  @Value("\${complete-check.delay-seconds}") completeCheckDelaySeconds: Int,
  @Value("\${complete-check.count}") completeCheckCount: Int,
) :
  MigrationService<CourtSentencingMigrationFilter, CourtCaseIdResponse, CourtCaseResponse, CourtCaseAllMappingDto>(
    queueService = queueService,
    auditService = auditService,
    migrationHistoryService = migrationHistoryService,
    mappingService = courtSentencingMappingService,
    telemetryClient = telemetryClient,
    migrationType = MigrationType.ADJUDICATIONS,
    pageSize = pageSize,
    completeCheckDelaySeconds = completeCheckDelaySeconds,
    completeCheckCount = completeCheckCount,
  ) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  override suspend fun getIds(
    migrationFilter: CourtSentencingMigrationFilter,
    pageSize: Long,
    pageNumber: Long,
  ): PageImpl<CourtCaseIdResponse> {
    return nomisApiService.getCourtCaseIds(
      fromDate = migrationFilter.fromDate,
      toDate = migrationFilter.toDate,
      pageNumber = pageNumber,
      pageSize = pageSize,
    )
  }

  override suspend fun migrateNomisEntity(context: MigrationContext<CourtCaseIdResponse>) {
    log.info("attempting to migrate ${context.body}")
    val caseId = context.body.caseId

    courtSentencingMappingService.getCourtCaseOrNullByNomisId(
      courtCaseId = caseId,
    )?.run {
      log.info("Will not migrate the court case since it is migrated already, NOMIS caseId is $caseId as part of migration ${this.label ?: "NONE"} (${this.mappingType})")
    } ?: run {
      val nomisCourtCase =
        nomisApiService.getCourtCaseForMigration(
          courtCaseId = caseId,
        )

      courtSentencingDpsService.createCourtCase(nomisCourtCase.toDpsCourtCase()).also {
        createCourtCaseMapping(it, context = context)
        telemetryClient.trackEvent(
          "court-case-migration-entity-migrated",
          mapOf(
            "caseId" to caseId.toString(),
            "offenderNo" to nomisCourtCase.offenderNo,
            "migrationId" to context.migrationId,
          ),
          null,
        )
      }
    }
  }

  // TODO create and persist mapping tree
  private suspend fun createCourtCaseMapping(
    mapping: CreateCourtCaseResponse,
    context: MigrationContext<*>,
  ) = CourtCaseAllMappingDto(
    nomisCourtCaseId = 1,
    dpsCourtCaseId = "",
    courtCharges = emptyList(),
    courtAppearances = emptyList(),
  )
}
