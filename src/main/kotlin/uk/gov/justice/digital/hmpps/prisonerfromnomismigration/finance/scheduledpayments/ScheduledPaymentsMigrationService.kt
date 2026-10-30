package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.scheduledpayments

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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ScheduledPaymentMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonNumberAndRootOffenderId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.ByIdRangeMigrationService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.ByLastId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationPage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService

@Service
class ScheduledPaymentsMigrationService(
  private val nomisApiService: NomisApiService,
  private val scheduledPaymentsNomisApiService: ScheduledPaymentsNomisApiService,
  private val dpsApiService: FinanceDpsApiService,
  private val scheduledPaymentsMappingService: ScheduledPaymentsMappingService,
  jsonMapper: JsonMapper,
  @Value($$"${finance.page.size:1000}") pageSize: Long,
  @Value($$"${finance.complete-check.delay-seconds}") completeCheckDelaySeconds: Int,
  @Value($$"${finance.complete-check.retry-seconds:1}") completeCheckRetrySeconds: Int,
  @Value($$"${finance.complete-check.count}") completeCheckCount: Int,
  @Value($$"${complete-check.scheduled-retry-seconds}") completeCheckScheduledRetrySeconds: Int,
) : ByIdRangeMigrationService<Any, PrisonNumberAndRootOffenderId, ScheduledPaymentMappingDto>(
  mappingService = scheduledPaymentsMappingService,
  migrationType = MigrationType.FINANCE_SCHEDULED_PAYMENTS,
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

  override suspend fun getTotalNumberOfIds(migrationFilter: Any): Long = scheduledPaymentsNomisApiService.getActiveScheduledPaymentsCount().activeCount

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
    val scheduledPayments = scheduledPaymentsNomisApiService.getActivePrisonerScheduledPayments(rootOffenderId)

    scheduledPayments.forEach { scheduledPayment ->
      val alreadyMigratedMapping = scheduledPaymentsMappingService.getByNomisIdOrNull(scheduledPayment.id)
      alreadyMigratedMapping?.run {
        log.info(
          "Will not migrate the nomis scheduledPayment id={} and dpsId={} for prisoner {} since it was already mapped during migration {}",
          scheduledPayment.id,
          dpsId,
          prisonNumber,
          label,
        )
      } ?: run {
        val response = dpsApiService.createScheduledPayment(scheduledPayment.toCreateScheduledPaymentDto())
        val mapping = ScheduledPaymentMappingDto(
          nomisScheduledPaymentId = scheduledPayment.id,
          dpsId = response.subscriptionId.toString(),
          mappingType = ScheduledPaymentMappingDto.MappingType.MIGRATED,
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
  override suspend fun retryCreateMapping(context: MigrationContext<ScheduledPaymentMappingDto>) = createMappingOrOnFailureDo(context, context.body) {
    throw it
  }

  suspend fun createMappingOrOnFailureDo(
    context: MigrationContext<*>,
    mapping: ScheduledPaymentMappingDto,
    failureHandler: suspend (error: Throwable) -> Unit,
  ) {
    runCatching {
      scheduledPaymentsMappingService.createMapping(
        mapping,
        object :
          ParameterizedTypeReference<DuplicateErrorResponse<ScheduledPaymentMappingDto>>() {},
      )
    }.onFailure {
      failureHandler(it)
    }.onSuccess {
      if (it.isError) {
        val duplicateErrorDetails = it.errorResponse!!.moreInfo
        telemetryClient.trackEvent(
          "scheduled-payments-migration-duplicate",
          mapOf(
            "duplicateDpsScheduledPaymentId" to duplicateErrorDetails.duplicate.dpsId,
            "duplicateNomisScheduledPaymentId" to duplicateErrorDetails.duplicate.nomisScheduledPaymentId,
            "existingDpsScheduledPaymentId" to duplicateErrorDetails.existing.dpsId,
            "existingNomisScheduledPaymentId" to duplicateErrorDetails.existing.nomisScheduledPaymentId,
            "migrationId" to context.migrationId,
          ),
        )
      } else {
        telemetryClient.trackEvent(
          "scheduled-payments-migration-entity-migrated",
          mapOf(
            "nomisScheduledPaymentId" to mapping.nomisScheduledPaymentId,
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

  override fun parseContextMapping(json: String): MigrationMessage<*, ScheduledPaymentMappingDto> = jsonMapper.readValue(json)
}
