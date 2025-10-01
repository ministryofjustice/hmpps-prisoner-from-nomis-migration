package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageImpl
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.model.PrisonerAccountPointInTimeBalance
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.model.PrisonerBalancesSyncRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PrisonerBalanceMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PrisonerBalanceMappingDto.MappingType.MIGRATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerAccountDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerBalanceDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.toPageImpl
import java.math.BigDecimal
import java.time.LocalDateTime

@Service
class PrisonerBalanceMigrationService(
  val nomisApiService: NomisApiService,
  val prisonerBalanceMappingService: PrisonerBalanceMappingApiService,
  val prisonerBalanceNomisApiService: PrisonerBalanceNomisApiService,
  val dpsApiService: FinanceApiService,
  @Value($$"${prisonerbalance.page.size:1000}") pageSize: Long,
  @Value($$"${complete-check.delay-seconds}") completeCheckDelaySeconds: Int,
  @Value($$"${complete-check.count}") completeCheckCount: Int,
) : MigrationService<PrisonerBalanceMigrationFilter, Long, PrisonerBalanceMappingDto>(
  mappingService = prisonerBalanceMappingService,
  migrationType = MigrationType.PRISONER_BALANCE,
  pageSize = pageSize,
  completeCheckDelaySeconds = completeCheckDelaySeconds,
  completeCheckCount = completeCheckCount,
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  override suspend fun getIds(
    migrationFilter: PrisonerBalanceMigrationFilter,
    pageSize: Long,
    pageNumber: Long,
  ): PageImpl<Long> = prisonerBalanceNomisApiService.getRootOffenderIdsToMigrate(
    prisonId = migrationFilter.prisonId,
    pageNumber = pageNumber,
    pageSize = pageSize,
  ).toPageImpl()

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
      prisonerBalanceMappingService.createMapping(mapping)
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
}

fun PrisonerBalanceDto.toMigrationDto() = PrisonerBalancesSyncRequest(
  accountBalances = accounts.map { it.toMigrationDto() },
)

fun PrisonerAccountDto.toMigrationDto() = PrisonerAccountPointInTimeBalance(
  prisonId = prisonId,
  accountCode = accountCode.toInt(),
  balance = balance,
  holdBalance = holdBalance ?: BigDecimal.ZERO,
  // TODO: This is passing through timestamp, but think we need transactionId instead?
  asOfTimestamp = LocalDateTime.now(),
)
