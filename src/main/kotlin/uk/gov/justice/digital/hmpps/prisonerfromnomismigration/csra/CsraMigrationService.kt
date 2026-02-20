package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csra

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.SuccessOrDuplicate
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CsraMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CsraMappingIdDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PrisonerCsraMappingsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.ByPageNumber
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.ByPageNumberMigrationService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationPage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService

@Service
class CsraMigrationService(
  val nomisApiService: NomisApiService,
  val csraMappingService: CsraMappingService,
  val csraNomisApiService: CsraNomisApiService,
  val csraApiService: CsraApiService,
  jsonMapper: JsonMapper,
  @Value($$"${csra.page.size:1000}") pageSize: Long,
  @Value($$"${complete-check.delay-seconds}") completeCheckDelaySeconds: Int,
  @Value($$"${complete-check.count}") completeCheckCount: Int,
) : ByPageNumberMigrationService<PrisonerMigrationFilter, PrisonerId, CsraMigrationMapping>(
  mappingService = csraMappingService,
  migrationType = MigrationType.CSRA,
  pageSize = pageSize,
  completeCheckDelaySeconds = completeCheckDelaySeconds,
  completeCheckCount = completeCheckCount,
  jsonMapper = jsonMapper,
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun getIds(
    migrationFilter: PrisonerMigrationFilter,
    pageSize: Long,
    pageNumber: Long,
  ) = migrationFilter.offenderNos?.let { offenderNos ->
    PageImpl(
      offenderNos.map { PrisonerId(it) },
      Pageable.ofSize(offenderNos.size),
      offenderNos.size.toLong(),
    )
  }
    ?: nomisApiService.getPrisonerIds(
      pageNumber = pageNumber,
      pageSize = pageSize,
    )

  override suspend fun getPageOfIds(
    migrationFilter: PrisonerMigrationFilter,
    pageSize: Long,
    pageNumber: Long,
  ): List<PrisonerId> = getIds(migrationFilter, pageSize, pageNumber).content

  override suspend fun getTotalNumberOfIds(migrationFilter: PrisonerMigrationFilter): Long = getIds(
    migrationFilter,
    1,
    0,
  ).totalElements

  override suspend fun migrateNomisEntity(context: MigrationContext<PrisonerId>) {
    val offenderNo = context.body.offenderNo
    log.info("attempting to migrate $offenderNo")
    val telemetry = mutableMapOf(
      "offenderNo" to offenderNo,
      "migrationId" to context.migrationId,
      "migrationType" to "CSRA",
    )
    try {
      val csrasToMigrate = csraNomisApiService
        .getCsras(offenderNo)
        .csras
        .map { it.toDPSCreateCaseNote() }

      csraApiService.migrateCsras(offenderNo, csrasToMigrate)
        .also { migrationResultList ->
          val prisonerCsraMappingsDto = PrisonerCsraMappingsDto(
            mappings = migrationResultList.map {
              CsraMappingIdDto(
                dpsCsraId = it.dpsCsraId,
                nomisBookingId = it.nomisBookingId,
                nomisSequence = it.nomisSequence,
              )
            },
            mappingType = PrisonerCsraMappingsDto.MappingType.MIGRATED,
            label = context.migrationId,
          )
          createMappingOrOnFailureDo(
            CsraMigrationMapping(
              prisonerCsraMappingsDto,
              offenderNo = offenderNo,
            ),
          ) {
            log.error("Failed to create mapping for csras for prisoner ${context.body.offenderNo}", it)
            queueService.sendMessage(
              MigrationMessageType.RETRY_MIGRATION_MAPPING,
              MigrationContext(
                context = context,
                body = CsraMigrationMapping(prisonerCsraMappingsDto, offenderNo = offenderNo),
              ),
            )
          }
          telemetry["csraCount"] = migrationResultList.size.toString()
          telemetryClient.trackEvent("csras-migration-entity-migrated", telemetry)
        }
    } catch (e: Exception) {
      telemetry["error"] = e.message ?: "unknown error"
      telemetryClient.trackEvent("csras-migration-entity-failed", telemetry)
      throw e
    }
  }

  override suspend fun retryCreateMapping(context: MigrationContext<CsraMigrationMapping>) {
    createMappingOrOnFailureDo(context.body) {
      throw it
    }
  }

  suspend fun createMappingOrOnFailureDo(
    mapping: CsraMigrationMapping,
    failureHandler: suspend (error: Throwable) -> Unit,
  ) {
    runCatching {
      csraMappingService.createMapping(mapping)
    }.onFailure {
      failureHandler(it)
    }.onSuccess {
      publishTelemetry(it, mapping)
    }
  }

  private fun publishTelemetry(
    successOrDuplicate: SuccessOrDuplicate<CsraMappingDto>,
    mapping: CsraMigrationMapping,
  ) {
    if (successOrDuplicate.isError) {
      val duplicateErrorDetails = successOrDuplicate.errorResponse!!.moreInfo
      telemetryClient.trackEvent(
        "csras-migration-entity-duplicate",
        mapOf(
          "offenderNo" to mapping.offenderNo,
          "migrationId" to mapping.prisonerMappings.label!!,
          "duplicateDpsCsraId" to duplicateErrorDetails.duplicate.dpsCsraId,
          "duplicateNomisBookingId" to duplicateErrorDetails.duplicate.nomisBookingId.toString(),
          "duplicateNomisSequence" to duplicateErrorDetails.duplicate.nomisSequence.toString(),
          "existingDpsCsraId" to duplicateErrorDetails.existing?.dpsCsraId.toString(),
          "existingNomisBookingId" to duplicateErrorDetails.existing?.nomisBookingId.toString(),
          "existingNomisSequence" to duplicateErrorDetails.existing?.nomisSequence.toString(),
        ),
      )
    }
  }

  override fun parseContextFilter(json: String): MigrationMessage<*, PrisonerMigrationFilter> = jsonMapper
    .readValue(json)

  override fun parseContextPageFilter(json: String): MigrationMessage<*, MigrationPage<PrisonerMigrationFilter, ByPageNumber>> = jsonMapper
    .readValue(json)

  override fun parseContextNomisId(json: String): MigrationMessage<*, PrisonerId> = jsonMapper
    .readValue(json)

  override fun parseContextMapping(json: String): MigrationMessage<*, CsraMigrationMapping> = jsonMapper
    .readValue(json)
}

data class CsraMigrationMapping(
  val prisonerMappings: PrisonerCsraMappingsDto, // from nomis-api
  val offenderNo: String,
)
