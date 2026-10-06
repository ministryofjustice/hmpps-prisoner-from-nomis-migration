package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.advances

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.FinanceDpsApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.AdvanceMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.AdvanceMappingDto.MappingType.MIGRATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonNumberAndRootOffenderId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.ByIdRangeMigrationService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.ByLastId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationPage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService

@Service
class AdvancesMigrationService(
  private val nomisApiService: NomisApiService,
  private val advancesNomisApiService: AdvancesNomisApiService,
  private val dpsApiService: FinanceDpsApiService,
  private val advancesMappingService: AdvancesMappingService,
  jsonMapper: JsonMapper,
  @Value($$"${advances.page.size:1000}") pageSize: Long,
  @Value($$"${advances.complete-check.delay-seconds}") completeCheckDelaySeconds: Int,
  @Value($$"${complete-check.retry-seconds:1}") completeCheckRetrySeconds: Int,
  @Value($$"${advances.complete-check.count}") completeCheckCount: Int,
  @Value($$"${complete-check.scheduled-retry-seconds}") completeCheckScheduledRetrySeconds: Int,
) : ByIdRangeMigrationService<Any, PrisonNumberAndRootOffenderId, AdvanceMappingDto>(
  mappingService = advancesMappingService,
  migrationType = MigrationType.ADVANCES,
  pageSize = pageSize,
  completeCheckDelaySeconds = completeCheckDelaySeconds,
  completeCheckCount = completeCheckCount,
  completeCheckRetrySeconds = completeCheckRetrySeconds,
  completeCheckScheduledRetrySeconds = completeCheckScheduledRetrySeconds,
  jsonMapper = jsonMapper,
) {
  private companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  // TODO Should this be the total number of active advances?
  override suspend fun getTotalNumberOfIds(migrationFilter: Any): Long = nomisApiService.getPrisonerIds(0, 1).totalElements

  override suspend fun getMigrationCount(migrationId: String): Long = mappingService.getPagedModelMigrationCount(migrationId)

  override suspend fun getRangeOfIds(
    body: Any,
    pageSize: Long,
  ): List<Pair<PrisonNumberAndRootOffenderId, PrisonNumberAndRootOffenderId>> = nomisApiService.getAllPrisonersIdRanges(pageSize)
    .map {
      Pair(
        PrisonNumberAndRootOffenderId(it.fromId, ""),
        PrisonNumberAndRootOffenderId(it.toId, ""),
      )
    }

  override suspend fun getPageOfIdsFromIdRange(
    firstId: PrisonNumberAndRootOffenderId?,
    lastId: PrisonNumberAndRootOffenderId?,
    migrationFilter: Any,
  ): List<PrisonNumberAndRootOffenderId> = nomisApiService.getAllPrisonersInRange(firstId!!.rootOffenderId, lastId!!.rootOffenderId)

  override suspend fun migrateNomisEntity(context: MigrationContext<PrisonNumberAndRootOffenderId>) {
    val prisonNumber = context.body.prisonNumber
    val rootOffenderId = context.body.rootOffenderId
    val advances = advancesNomisApiService.getPrisonerAdvances(rootOffenderId)

    advances.forEach { advance ->
      val alreadyMigratedMapping = advancesMappingService.getByNomisIdOrNull(advance.id)
      alreadyMigratedMapping?.run {
        log.info(
          "Will not migrate the nomis advance id={} and dpsId={} for prisoner {} since it was already mapped during migration {}",
          advance.id,
          dpsId,
          prisonNumber,
          label,
        )
      } ?: run {
        val response = dpsApiService.migrateAdvance(advance.toSyncAdvanceDto())
        val mapping = AdvanceMappingDto(
          nomisAdvanceId = advance.id,
          dpsId = response.advanceUuid.toString(),
          mappingType = MIGRATED,
          label = context.migrationId,
        )

        createMappingOrOnFailureDo(context, mapping) {
          queueService.sendMessage(
            MigrationMessageType.RETRY_MIGRATION_MAPPING,
            MigrationContext(
              context = context,
              body = mapping,
            ),
          )
        }
      }
    }
  }
  suspend fun createMappingOrOnFailureDo(
    context: MigrationContext<*>,
    mapping: AdvanceMappingDto,
    failureHandler: suspend (error: Throwable) -> Unit,
  ) {
    runCatching {
      advancesMappingService.createMapping(
        mapping,
        object :
          ParameterizedTypeReference<DuplicateErrorResponse<AdvanceMappingDto>>() {},
      )
    }.onFailure {
      failureHandler(it)
    }.onSuccess {
      if (it.isError) {
        val duplicateErrorDetails = it.errorResponse!!.moreInfo
        telemetryClient.trackEvent(
          "advances-migration-duplicate",
          mapOf(
            "duplicateDpsAdvanceId" to duplicateErrorDetails.duplicate.dpsId,
            "duplicateNomisAdvanceId" to duplicateErrorDetails.duplicate.nomisAdvanceId,
            "existingDpsAdvanceId" to duplicateErrorDetails.existing.dpsId,
            "existingNomisAdvanceId" to duplicateErrorDetails.existing.nomisAdvanceId,
            "migrationId" to context.migrationId,
          ),
        )
      } else {
        telemetryClient.trackEvent(
          "advances-migration-entity-migrated",
          mapOf(
            "nomisAdvanceId" to mapping.nomisAdvanceId,
            "dpsId" to mapping.dpsId,
            "migrationId" to context.migrationId,
          ),
        )
      }
    }
  }
  override fun parseContextFilter(json: String): MigrationMessage<*, Any> = jsonMapper.readValue(json)

  override fun parseContextPageFilter(json: String): MigrationMessage<*, MigrationPage<Any, ByLastId<PrisonNumberAndRootOffenderId>>> = jsonMapper.readValue(json)

  override fun parseContextNomisId(json: String): MigrationMessage<*, PrisonNumberAndRootOffenderId> = jsonMapper.readValue(json)

  override fun parseContextMapping(json: String): MigrationMessage<*, AdvanceMappingDto> = jsonMapper.readValue(json)
}
