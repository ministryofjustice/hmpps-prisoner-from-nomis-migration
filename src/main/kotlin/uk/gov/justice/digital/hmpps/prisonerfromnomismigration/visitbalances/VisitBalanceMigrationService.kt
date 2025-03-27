package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visitbalances

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageImpl
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.VisitBalanceMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.VisitBalanceMappingDto.MappingType.MIGRATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.VisitBalanceDetailResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.VisitBalanceIdResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visit.balance.model.VisitAllocationPrisonerMigrationDto
import java.time.LocalDate

@Service
class VisitBalanceMigrationService(
  val nomisApiService: NomisApiService,
  val visitBalanceMappingService: VisitBalanceMappingApiService,
  val visitBalanceNomisApiService: VisitBalanceNomisApiService,
  val dpsApiService: VisitBalanceDpsApiService,
  @Value("\${visitbalance.page.size:1000}") pageSize: Long,
  @Value("\${complete-check.delay-seconds}") completeCheckDelaySeconds: Int,
  @Value("\${complete-check.count}") completeCheckCount: Int,
) : MigrationService<VisitBalanceMigrationFilter, VisitBalanceIdResponse, VisitBalanceMappingDto>(
  mappingService = visitBalanceMappingService,
  migrationType = MigrationType.VISIT_BALANCE,
  pageSize = pageSize,
  completeCheckDelaySeconds = completeCheckDelaySeconds,
  completeCheckCount = completeCheckCount,
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  override suspend fun getIds(
    migrationFilter: VisitBalanceMigrationFilter,
    pageSize: Long,
    pageNumber: Long,
  ): PageImpl<VisitBalanceIdResponse> = visitBalanceNomisApiService.getVisitBalanceIds(
    prisonId = migrationFilter.prisonId,
    pageNumber = pageNumber,
    pageSize = pageSize,
  )

  override suspend fun migrateNomisEntity(context: MigrationContext<VisitBalanceIdResponse>) {
    val nomisVisitBalanceId = context.body.visitBalanceId
    val alreadyMigratedMapping = visitBalanceMappingService.getByNomisVisitBalanceIdOrNull(nomisVisitBalanceId)

    alreadyMigratedMapping?.run {
      log.info("Will not migrate the nomis visit balance id={} and prison number={} since it was already mapped during migration {}", nomisVisitBalanceId, dpsId, label)
    } ?: run {
      val visitBalance = visitBalanceNomisApiService.getVisitBalance(nomisVisitBalanceId)
      dpsApiService.migrateVisitBalance(visitBalance.toMigrationDto())
      val mapping = VisitBalanceMappingDto(nomisVisitBalanceId = nomisVisitBalanceId, dpsId = visitBalance.prisonNumber, mappingType = MIGRATED, label = context.migrationId)
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

  override suspend fun retryCreateMapping(context: MigrationContext<VisitBalanceMappingDto>) = createMappingOrOnFailureDo(context, context.body) {
    throw it
  }

  suspend fun createMappingOrOnFailureDo(
    context: MigrationContext<*>,
    mapping: VisitBalanceMappingDto,
    failureHandler: suspend (error: Throwable) -> Unit,
  ) {
    runCatching {
      visitBalanceMappingService.createMapping(mapping)
    }.onFailure {
      failureHandler(it)
    }.onSuccess {
      if (it.isError) {
        val duplicateErrorDetails = it.errorResponse!!.moreInfo
        telemetryClient.trackEvent(
          "nomis-migration-visitbalance-duplicate",
          mapOf(
            "duplicateDpsPrisonerId" to duplicateErrorDetails.duplicate.dpsId,
            "duplicateNomisVisitBalanceId" to duplicateErrorDetails.duplicate.nomisVisitBalanceId,
            "existingDpsPrisonerId" to duplicateErrorDetails.existing.dpsId,
            "existingNomisVisitBalanceId" to duplicateErrorDetails.existing.nomisVisitBalanceId,
            "migrationId" to context.migrationId,
          ),
        )
      } else {
        telemetryClient.trackEvent(
          "visitbalance-migration-entity-migrated",
          mapOf(
            "nomisVisitBalanceId" to mapping.nomisVisitBalanceId,
            "dpsPrisonerId" to mapping.dpsId,
            "migrationId" to context.migrationId,
          ),
        )
      }
    }
  }
}

fun VisitBalanceDetailResponse.toMigrationDto() = VisitAllocationPrisonerMigrationDto(
  voBalance = remainingVisitOrders,
  pvoBalance = remainingPrivilegedVisitOrders,
  lastVoAllocationDate = lastIEPAllocationDate ?: LocalDate.now().minusDays(14),
  prisonerId = prisonNumber,
)
