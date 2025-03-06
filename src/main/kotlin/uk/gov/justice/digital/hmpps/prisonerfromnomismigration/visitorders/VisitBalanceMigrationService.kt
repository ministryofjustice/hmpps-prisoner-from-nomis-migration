package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visitorders

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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerVisitOrderBalanceResponse
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
) : MigrationService<VisitBalanceMigrationFilter, PrisonerId, VisitBalanceMappingDto>(
  mappingService = visitBalanceMappingService,
  migrationType = MigrationType.VISIT_BALANCE,
  pageSize = pageSize,
  completeCheckDelaySeconds = completeCheckDelaySeconds,
  completeCheckCount = completeCheckCount,
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  // TODO Set up filtering
  override suspend fun getIds(
    migrationFilter: VisitBalanceMigrationFilter,
    pageSize: Long,
    pageNumber: Long,
  ): PageImpl<PrisonerId> = nomisApiService.getPrisonerIds(
    pageNumber = pageNumber,
    pageSize = pageSize,
  )

  override suspend fun migrateNomisEntity(context: MigrationContext<PrisonerId>) {
    val nomisPrisonNumber = context.body.offenderNo
    val alreadyMigratedMapping = visitBalanceMappingService.getByNomisPrisonNumberOrNull(nomisPrisonNumber)

    alreadyMigratedMapping?.run {
      log.info("Will not migrate the nomis prisoner=$nomisPrisonNumber since it was already mapped during migration ${this.label}")
    } ?: run {
      val visitBalance = visitBalanceNomisApiService.getVisitBalance(prisonNumber = nomisPrisonNumber)
      dpsApiService.migrateVisitBalance(visitBalance.toMigrationDto(nomisPrisonNumber))
      val mapping = VisitBalanceMappingDto(nomisPrisonNumber = nomisPrisonNumber, dpsId = nomisPrisonNumber, mappingType = MIGRATED, label = context.migrationId)
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
            "duplicateNomisPrisonNumber" to duplicateErrorDetails.duplicate.nomisPrisonNumber,
            "existingDpsPrisonerId" to duplicateErrorDetails.existing.dpsId,
            "existingNomisPrisonNumber" to duplicateErrorDetails.existing.nomisPrisonNumber,
            "migrationId" to context.migrationId,
          ),
        )
      } else {
        telemetryClient.trackEvent(
          "visitbalance-migration-entity-migrated",
          mapOf(
            "nomisPrisonNumber" to mapping.nomisPrisonNumber,
            "dpsPrisonerId" to mapping.dpsId,
            "migrationId" to context.migrationId,
          ),
        )
      }
    }
  }
}

fun PrisonerVisitOrderBalanceResponse.toMigrationDto(nomisPrisonNumber: String) = VisitAllocationPrisonerMigrationDto(
  voBalance = remainingVisitOrders,
  pvoBalance = remainingPrivilegedVisitOrders,
  // TOD add in the correct value
  lastVoAllocationDate = LocalDate.of(2025, 1, 15),
  prisonerId = nomisPrisonNumber,
)
