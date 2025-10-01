package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageImpl
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.model.InitialGeneralLedgerBalance
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.model.InitialGeneralLedgerBalancesRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PrisonBalanceMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PrisonBalanceMappingDto.MappingType.MIGRATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonAccountBalanceDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonBalanceDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService

@Service
class PrisonBalanceMigrationService(
  val nomisApiService: NomisApiService,
  val prisonBalanceMappingService: PrisonBalanceMappingApiService,
  val prisonBalanceNomisApiService: FinanceNomisApiService,
  val dpsApiService: FinanceApiService,
  @Value($$"${prisonBalance.page.size:1000}") pageSize: Long,
  @Value($$"${complete-check.delay-seconds}") completeCheckDelaySeconds: Int,
  @Value($$"${complete-check.count}") completeCheckCount: Int,
) : MigrationService<PrisonBalanceMigrationFilter, String, PrisonBalanceMappingDto>(
  mappingService = prisonBalanceMappingService,
  migrationType = MigrationType.PRISON_BALANCE,
  pageSize = pageSize,
  completeCheckDelaySeconds = completeCheckDelaySeconds,
  completeCheckCount = completeCheckCount,
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  override suspend fun getIds(
    migrationFilter: PrisonBalanceMigrationFilter,
    pageSize: Long,
    pageNumber: Long,
  ): PageImpl<String> = PageImpl(
    migrationFilter.prisonId?.let { listOf(it) } ?: prisonBalanceNomisApiService.getPrisonBalanceIds(),
  )

  override suspend fun migrateNomisEntity(context: MigrationContext<String>) {
    val prisonId = context.body
    val alreadyMigratedMapping = prisonBalanceMappingService.getByNomisIdOrNull(prisonId)

    alreadyMigratedMapping?.run {
      log.info("Will not migrate the nomis id={} since it was already mapped during migration {}", prisonId, label)
    } ?: run {
      val prisonBalance = prisonBalanceNomisApiService.getPrisonBalance(prisonId)
      dpsApiService.migratePrisonBalance(prisonBalance.prisonId, prisonBalance.toMigrationDto())
      val mapping = PrisonBalanceMappingDto(nomisId = prisonId, dpsId = prisonBalance.prisonId, mappingType = MIGRATED, label = context.migrationId)
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

  override suspend fun retryCreateMapping(context: MigrationContext<PrisonBalanceMappingDto>) = createMappingOrOnFailureDo(context, context.body) {
    throw it
  }

  suspend fun createMappingOrOnFailureDo(
    context: MigrationContext<*>,
    mapping: PrisonBalanceMappingDto,
    failureHandler: suspend (error: Throwable) -> Unit,
  ) {
    runCatching {
      prisonBalanceMappingService.createPrisonBalanceMapping(mapping)
    }.onFailure {
      failureHandler(it)
    }.onSuccess {
      if (it.isError) {
        val duplicateErrorDetails = it.errorResponse!!.moreInfo
        telemetryClient.trackEvent(
          "prisonbalance-migration-duplicate",
          mapOf(
            "duplicateDpsId" to duplicateErrorDetails.duplicate.dpsId,
            "duplicateNomisId" to duplicateErrorDetails.duplicate.nomisId,
            "existingDpsId" to duplicateErrorDetails.existing.dpsId,
            "existingNomisId" to duplicateErrorDetails.existing.nomisId,
            "migrationId" to context.migrationId,
          ),
        )
      } else {
        telemetryClient.trackEvent(
          "prisonbalance-migration-entity-migrated",
          mapOf(
            "nomisId" to mapping.nomisId,
            "dpsId" to mapping.dpsId,
            "migrationId" to context.migrationId,
          ),
        )
      }
    }
  }
}

fun PrisonBalanceDto.toMigrationDto() = InitialGeneralLedgerBalancesRequest(
  initialBalances = accountBalances.map { it.toMigrationDto() },
)

fun PrisonAccountBalanceDto.toMigrationDto() = InitialGeneralLedgerBalance(
  accountCode = accountCode.toInt(),
  balance = balance,
)
