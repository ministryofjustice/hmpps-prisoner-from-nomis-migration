package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.model.PrisonerAccountPointInTimeBalance
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.model.PrisonerBalancesSyncRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PrisonerBalanceMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PrisonerBalanceMappingDto.MappingType.MIGRATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerAccountDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerBalanceDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.ByPageNumber
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.ByPageNumberMigrationService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationPage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService
import java.math.BigDecimal

@Service
class PrisonerBalanceMigrationService(
  val nomisApiService: NomisApiService,
  val prisonerBalanceMappingService: PrisonerBalanceMappingApiService,
  val prisonerBalanceNomisApiService: PrisonerBalanceNomisApiService,
  val dpsApiService: FinanceApiService,
  jsonMapper: JsonMapper,
  @Value($$"${prisonerbalance.page.size:1000}") pageSize: Long,
  @Value($$"${complete-check.delay-seconds}") completeCheckDelaySeconds: Int,
  @Value($$"${complete-check.count}") completeCheckCount: Int,
) : ByPageNumberMigrationService<PrisonerBalanceMigrationFilter, Long, PrisonerBalanceMappingDto>(
  mappingService = prisonerBalanceMappingService,
  migrationType = MigrationType.PRISONER_BALANCE,
  pageSize = pageSize,
  completeCheckDelaySeconds = completeCheckDelaySeconds,
  completeCheckCount = completeCheckCount,
  jsonMapper = jsonMapper,
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  override suspend fun getMigrationCount(migrationId: String): Long = prisonerBalanceMappingService.getPagedModelMigrationCount(migrationId)

  suspend fun getIds(
    migrationFilter: PrisonerBalanceMigrationFilter,
    pageSize: Long,
    pageNumber: Long,
  ) = prisonerBalanceNomisApiService.getRootOffenderIdsToMigrate(
    prisonId = migrationFilter.prisonId,
    pageNumber = pageNumber,
    pageSize = pageSize,
  )

  override suspend fun getPageOfIds(
    migrationFilter: PrisonerBalanceMigrationFilter,
    pageSize: Long,
    pageNumber: Long,
  ): List<Long> = getIds(migrationFilter, pageSize, pageNumber).content

  override suspend fun getTotalNumberOfIds(migrationFilter: PrisonerBalanceMigrationFilter): Long = getIds(migrationFilter, 1, 0).metadata.totalElements

  override suspend fun migrateNomisEntity(context: MigrationContext<Long>) {
    val nomisRootOffenderId = context.body
    val alreadyMigratedMapping = prisonerBalanceMappingService.getByNomisIdOrNull(nomisRootOffenderId)

    alreadyMigratedMapping?.run {
      log.info("Will not migrate the nomis root offender id={} and prison number={} since it was already mapped during migration {}", nomisRootOffenderId, dpsId, label)
    } ?: run {
      val prisonerBalance = prisonerBalanceNomisApiService.getPrisonerBalance(nomisRootOffenderId)
      dpsApiService.migratePrisonerBalance(prisonerBalance.prisonNumber, prisonerBalance.toMigrationDto())
      val mapping = PrisonerBalanceMappingDto(nomisRootOffenderId = nomisRootOffenderId, dpsId = prisonerBalance.prisonNumber, mappingType = MIGRATED, label = context.migrationId)
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

  override suspend fun retryCreateMapping(context: MigrationContext<PrisonerBalanceMappingDto>) = createMappingOrOnFailureDo(context, context.body) {
    throw it
  }

  suspend fun createMappingOrOnFailureDo(
    context: MigrationContext<*>,
    mapping: PrisonerBalanceMappingDto,
    failureHandler: suspend (error: Throwable) -> Unit,
  ) {
    runCatching {
      prisonerBalanceMappingService.createMapping(
        mapping,
        object :
          ParameterizedTypeReference<DuplicateErrorResponse<PrisonerBalanceMappingDto>>() {},
      )
    }.onFailure {
      failureHandler(it)
    }.onSuccess {
      if (it.isError) {
        val duplicateErrorDetails = it.errorResponse!!.moreInfo
        telemetryClient.trackEvent(
          "prisonerbalance-migration-duplicate",
          mapOf(
            "duplicateDpsPrisonerId" to duplicateErrorDetails.duplicate.dpsId,
            "duplicateNomisRootOffenderId" to duplicateErrorDetails.duplicate.nomisRootOffenderId,
            "existingDpsPrisonerId" to duplicateErrorDetails.existing.dpsId,
            "existingNomisRootOffenderId" to duplicateErrorDetails.existing.nomisRootOffenderId,
            "migrationId" to context.migrationId,
          ),
        )
      } else {
        telemetryClient.trackEvent(
          "prisonerbalance-migration-entity-migrated",
          mapOf(
            "nomisRootOffenderId" to mapping.nomisRootOffenderId,
            "dpsPrisonerId" to mapping.dpsId,
            "migrationId" to context.migrationId,
          ),
        )
      }
    }
  }
  override fun parseContextFilter(json: String): MigrationMessage<*, PrisonerBalanceMigrationFilter> = jsonMapper.readValue(json)

  override fun parseContextPageFilter(json: String): MigrationMessage<*, MigrationPage<PrisonerBalanceMigrationFilter, ByPageNumber>> = jsonMapper.readValue(json)

  override fun parseContextNomisId(json: String): MigrationMessage<*, Long> = jsonMapper.readValue(json)

  override fun parseContextMapping(json: String): MigrationMessage<*, PrisonerBalanceMappingDto> = jsonMapper.readValue(json)
}

fun PrisonerBalanceDto.toMigrationDto() = PrisonerBalancesSyncRequest(
  accountBalances = accounts.map { it.toMigrationDto() },
)

fun PrisonerAccountDto.toMigrationDto() = PrisonerAccountPointInTimeBalance(
  prisonId = prisonId,
  accountCode = accountCode.toInt(),
  balance = balance,
  holdBalance = holdBalance ?: BigDecimal.ZERO,
  asOfTimestamp = transactionDate,
  transactionId = lastTransactionId,
)
