package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.drugtesting

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.drugtesting.model.MigrationRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.drugtesting.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.IdRange
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.RandomTestingProgramResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.ByIdRangeMigrationService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.ByLastId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationPage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType.DRUG_TESTING

@Service
class DrugTestingMigrationService(
  private val drugTestingNomisApiService: DrugTestingNomisApiService,
  private val dpsApiService: DrugTestingDpsApiService,
  drugTestingMappingService: DrugTestingMappingService,
  jsonMapper: JsonMapper,
  @Value($$"${drugtesting.page.size:1000}") pageSize: Long,
  @Value($$"${drugtesting.complete-check.delay-seconds}") completeCheckDelaySeconds: Int,
  @Value($$"${drugtesting.complete-check.retry-seconds:1}") completeCheckRetrySeconds: Int,
  @Value($$"${drugtesting.complete-check.count}") completeCheckCount: Int,
  @Value($$"${complete-check.scheduled-retry-seconds}") completeCheckScheduledRetrySeconds: Int,
) : ByIdRangeMigrationService<DrugTestingMigrationFilter, Long, Any>(
  mappingService = drugTestingMappingService,
  migrationType = DRUG_TESTING,
  pageSize = pageSize,
  completeCheckDelaySeconds = completeCheckDelaySeconds,
  completeCheckCount = completeCheckCount,
  completeCheckRetrySeconds = completeCheckRetrySeconds,
  completeCheckScheduledRetrySeconds = completeCheckScheduledRetrySeconds,
  jsonMapper = jsonMapper,
) {
  override suspend fun getTotalNumberOfIds(migrationFilter: DrugTestingMigrationFilter): Long = -1L

  override suspend fun getRangeOfIds(body: DrugTestingMigrationFilter, pageSize: Long): List<Pair<Long, Long>> = drugTestingNomisApiService.getDrugTestingIdRanges(pageSize, filter = body)
    .map { Pair(it.fromId, it.toId) }

  override suspend fun getPageOfIdsFromIdRange(
    firstId: Long?,
    lastId: Long?,
    migrationFilter: DrugTestingMigrationFilter,
  ): List<Long> = drugTestingNomisApiService.getDrugTestingIdsInRange(IdRange(firstId!!, lastId!!), filter = migrationFilter)

  override suspend fun migrateNomisEntity(context: MigrationContext<Long>) {
    val programId = context.body
    val program = drugTestingNomisApiService.getRandomTestingProgram(programId)
    val telemetryContext = mapOf(
      "programId" to programId,
      "prisonId" to program.caseloadId,
      "rtpDate" to program.rtpDate,
      "migrationId" to context.migrationId,
    )
    if (program.offenderTestSelection.isEmpty()) {
      telemetryClient.trackEvent("drugtesting-migration-entity-ignored", telemetryContext)
      return
    }

    dpsApiService.migrate(program.caseloadId, program.rtpDate, program.toMigrationRequest())
    telemetryClient.trackEvent("drugtesting-migration-entity-migrated", telemetryContext)
  }

  override suspend fun getMigrationCount(migrationId: String): Long = -1

  override suspend fun retryCreateMapping(context: MigrationContext<Any>) { }

  override fun parseContextFilter(json: String): MigrationMessage<*, DrugTestingMigrationFilter> = jsonMapper.readValue(json)
  override fun parseContextPageFilter(json: String): MigrationMessage<*, MigrationPage<DrugTestingMigrationFilter, ByLastId<Long>>> = jsonMapper.readValue(json)

  override fun parseContextNomisId(json: String): MigrationMessage<*, Long> = jsonMapper.readValue(json)
  override fun parseContextMapping(json: String): MigrationMessage<*, Any> = jsonMapper.readValue(json)
}

private fun RandomTestingProgramResponse.toMigrationRequest(): MigrationRequest = MigrationRequest(
  dateGenerated = this.createdDateTime,
  createdBy = this.createdByUsername,
  mainPercentage = this.mainPercentage,
  reservePercentage = this.reservePercentage,
  mainCount = this.selectionsCount!!,
  reserveCount = this.reserveCount!!,
  eligibleCount = this.eligibleCount!!,
  prisoners = this.offenderTestSelection.map {
    Prisoner(
      prisonerNumber = it.prisonNumber,
      listType = it.testSelectionType,
      listSelectionNumber = it.testSelectionNo,
      testedStatus = it.testedFlag,
      reasonNotTested = it.reasonNotTested,
      notes = it.notes,
    )
  },
)
