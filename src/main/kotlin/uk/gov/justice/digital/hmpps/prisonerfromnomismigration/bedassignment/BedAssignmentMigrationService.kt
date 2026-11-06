package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.bedassignment

import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.SuccessOrDuplicate
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.BedAssignmentMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.BedAssignmentMappingIdDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PrisonerBedAssignmentMappingsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.ByPageNumber
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.ByPageNumberMigrationService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationPage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService

@Service
class BedAssignmentMigrationService(
  private val nomisApiService: NomisApiService,
  private val byPrisonerMappingApiService: BedAssignmentByPrisonerMappingApiService,
  private val translatorService: TranslatorService,
  private val dpsApiService: BedAssignmentDpsApiService,
  jsonMapper: JsonMapper,
  @Value($$"${bedassignment.page.size:1000}") pageSize: Long,
  @Value($$"${complete-check.delay-seconds}") completeCheckDelaySeconds: Int,
  @Value($$"${complete-check.count}") completeCheckCount: Int,
) : ByPageNumberMigrationService<PrisonerMigrationFilter, PrisonerId, BedAssignmentMigrationMapping>(
  mappingService = byPrisonerMappingApiService,
  migrationType = MigrationType.BED_ASSIGNMENT,
  pageSize = pageSize,
  completeCheckDelaySeconds = completeCheckDelaySeconds,
  completeCheckCount = completeCheckCount,
  jsonMapper = jsonMapper,
) {
  private companion object {
    val log = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun getIds(
    migrationFilter: PrisonerMigrationFilter,
    pageSize: Long,
    pageNumber: Long,
  ) = migrationFilter.offenderNos?.distinct()?.let { offenderNos ->
    val fromIndex = (pageNumber * pageSize).toInt()
    val toIndex = (fromIndex + pageSize).toInt()
    PageImpl(
      offenderNos.subList(fromIndex, toIndex).map { PrisonerId(it) },
      PageRequest.of(pageNumber.toInt(), pageSize.toInt()),
      offenderNos.size.toLong(),
    )
  } ?: nomisApiService.getPrisonerIds(pageNumber, pageSize)

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
    log.info("attempting to migrate {}", offenderNo)
    val telemetry = mutableMapOf(
      "offenderNo" to offenderNo,
      "migrationId" to context.migrationId,
      "migrationType" to MigrationType.BED_ASSIGNMENT.name,
    )
    try {
      val history = translatorService.getHistory(offenderNo)
      val result = dpsApiService.migratePrisoner(offenderNo, history)
      val mapping = BedAssignmentMigrationMapping(
        prisonerMappings = PrisonerBedAssignmentMappingsDto(
          mappings = result.map {
            BedAssignmentMappingIdDto(
              dpsBedAssignmentId = it.id.toString(),
              nomisBookingId = it.bookingId,
              nomisSequence = it.sequence,
            )
          },
          mappingType = PrisonerBedAssignmentMappingsDto.MappingType.MIGRATED,
          label = context.migrationId,
        ),
        offenderNo = offenderNo,
      )
      val mappingComplete = createMappingOrOnFailureDo(mapping) {
        log.error("Failed to create bed-assignment mappings for prisoner {}", offenderNo, it)
        queueService.sendMessage(
          MigrationMessageType.RETRY_MIGRATION_MAPPING,
          MigrationContext(context = context, body = mapping),
        )
      }
      telemetry["bedAssignmentCount"] = result.size.toString()
      telemetryClient.trackEvent(
        if (mappingComplete) "bedassignment-migration-entity-migrated" else "bedassignment-migration-entity-mapping-pending",
        telemetry,
      )
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      telemetry["error"] = e.message ?: e.javaClass.name
      telemetryClient.trackEvent("bedassignment-migration-entity-failed", telemetry)
      throw e
    }
  }

  override suspend fun retryCreateMapping(context: MigrationContext<BedAssignmentMigrationMapping>) {
    createMappingOrOnFailureDo(context.body) { throw it }
  }

  suspend fun createMappingOrOnFailureDo(
    mappings: BedAssignmentMigrationMapping,
    failureHandler: suspend (error: Throwable) -> Unit,
  ): Boolean {
    try {
      val result = byPrisonerMappingApiService.createMappings(mappings)
      publishTelemetry(result, mappings)
      if (result.isError) {
        byPrisonerMappingApiService.reconcileMappings(mappings) { publishTelemetry(it, mappings) }
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      failureHandler(e)
      return false
    }
    return true
  }

  private fun publishTelemetry(
    successOrDuplicate: SuccessOrDuplicate<BedAssignmentMappingDto>,
    mapping: BedAssignmentMigrationMapping,
  ) {
    successOrDuplicate.errorResponse?.moreInfo?.let {
      telemetryClient.trackEvent(
        "bedassignment-migration-entity-duplicate",
        mapOf(
          "offenderNo" to mapping.offenderNo,
          "migrationId" to mapping.prisonerMappings.label!!,
          "duplicateDpsBedAssignmentId" to it.duplicate.dpsBedAssignmentId,
          "duplicateNomisBookingId" to it.duplicate.nomisBookingId.toString(),
          "duplicateNomisSequence" to it.duplicate.nomisSequence.toString(),
          "existingDpsBedAssignmentId" to it.existing?.dpsBedAssignmentId.toString(),
          "existingNomisBookingId" to it.existing?.nomisBookingId.toString(),
          "existingNomisSequence" to it.existing?.nomisSequence.toString(),
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

  override fun parseContextMapping(json: String): MigrationMessage<*, BedAssignmentMigrationMapping> = jsonMapper
    .readValue(json)
}

data class BedAssignmentMigrationMapping(
  val prisonerMappings: PrisonerBedAssignmentMappingsDto,
  val offenderNo: String,
)
