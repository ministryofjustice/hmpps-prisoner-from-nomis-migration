package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.MigrationCreateCourtCases
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.MigrationMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CourtEventChargeIdResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.ByLastId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.ByLastIdMigrationService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationPage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType

@Service
class CourtSentencingMigrationService(
  @Qualifier("mappingApiWebClient") mappingApiWebClient: WebClient,
  private val courtSentencingNomisApiService: CourtSentencingNomisApiService,
  private val courtSentencingMappingService: CourtSentencingMappingApiService,
  private val courtSentencingDpsService: CourtSentencingDpsApiService,
  jsonMapper: JsonMapper,
  @Value("\${courtsentencing.page.size:1000}") pageSize: Long,
  @Value("\${courtsentencing.parallel.count:0}") getIdsParallelCount: Int,
  @Value("\${complete-check.delay-seconds}") completeCheckDelaySeconds: Int,
  @Value("\${complete-check.count}") completeCheckCount: Int,
) : ByLastIdMigrationService<Any, CourtEventChargeIdResponse, Any>(
  // no mapping api is used for this type of migration - placeholder implementation
  mappingService = object : MigrationMapping<Any>(domainUrl = "", webClient = mappingApiWebClient) {},
  migrationType = MigrationType.COURT_SENTENCING,
  pageSize = pageSize,
  getIdsParallelCount = getIdsParallelCount,
  completeCheckDelaySeconds = completeCheckDelaySeconds,
  completeCheckCount = completeCheckCount,
  jsonMapper = jsonMapper,
) {

  override suspend fun getPageOfIdsFromId(
    lastId: CourtEventChargeIdResponse?,
    migrationFilter: Any,
    pageSize: Long,
  ): List<CourtEventChargeIdResponse> = courtSentencingNomisApiService.getCourtEventChargeIdsWithPropertyValueFromId(
    lastOffenderChargeId = lastId?.offenderChargeId ?: 0,
    lastEventId = lastId?.eventId ?: 0,
    pageSize = pageSize.toInt(),
  ).courtEventChargeIds

  // ids are returned in ascending offenderChargeId/eventId order; a null second id is the open-ended end of the range
  override fun compare(first: CourtEventChargeIdResponse, second: CourtEventChargeIdResponse?): Int = if (second == null) {
    -1
  } else {
    compareValuesBy(first, second, { it.offenderChargeId }, { it.eventId })
  }

  // only used to seed parallel ranges (parallel count > 1)
  override suspend fun getPageOfIds(
    migrationFilter: Any,
    pageSize: Long,
    pageNumber: Long,
  ): List<CourtEventChargeIdResponse> = courtSentencingNomisApiService.getCourtEventChargeIdsWithPropertyValue(
    page = pageNumber.toInt(),
    size = pageSize.toInt(),
  ).content ?: emptyList()

  override suspend fun getTotalNumberOfIds(migrationFilter: Any): Long = 0

  // uses mapping service in super class - will prevent that call
  override suspend fun getMigrationCount(migrationId: String): Long = 0

  override suspend fun retryCreateMapping(context: MigrationContext<Any>) {}

  override suspend fun migrateNomisEntity(context: MigrationContext<CourtEventChargeIdResponse>) {
    val eventId = context.body.eventId
    val offenderChargeId = context.body.offenderChargeId
    val telemetry = mutableMapOf(
      "nomisCourtAppearanceId" to eventId.toString(),
      "nomisOffenderChargeId" to offenderChargeId.toString(),
      "migrationId" to context.migrationId,
    )

    val appearanceMapping = courtSentencingMappingService.getCourtAppearanceOrNullByNomisId(eventId)
    val chargeMapping = courtSentencingMappingService.getOffenderChargeOrNullByNomisId(offenderChargeId)
    if (appearanceMapping == null || chargeMapping == null) {
      telemetryClient.trackEvent(
        "court-event-charge-migration-entity-failed",
        telemetry + ("reason" to if (appearanceMapping == null) "court appearance mapping not found" else "court charge mapping not found"),
      )
      return
    }

    val nomisCharge = courtSentencingNomisApiService.getCourtEventChargeForMigration(
      eventId = eventId,
      offenderChargeId = offenderChargeId,
    )
    try {
      courtSentencingDpsService.updateCourtCharge(
        chargeId = chargeMapping.dpsCourtChargeId,
        appearanceId = appearanceMapping.dpsCourtAppearanceId,
        charge = nomisCharge.toDpsCharge(),
      )
    } catch (e: Exception) {
      telemetryClient.trackEvent(
        "court-event-charge-migration-entity-failed",
        telemetry + ("dpsChargeId" to chargeMapping.dpsCourtChargeId) + ("dpsCourtAppearanceId" to appearanceMapping.dpsCourtAppearanceId) + (
          "reason" to (
            e.message
              ?: e.javaClass.name
            )
          ),
      )
      throw e
    }
    telemetryClient.trackEvent(
      "court-event-charge-migration-entity-migrated",
      telemetry + ("dpsChargeId" to chargeMapping.dpsCourtChargeId) + ("dpsCourtAppearanceId" to appearanceMapping.dpsCourtAppearanceId),
    )
  }

  suspend fun offenderMigrationPayload(offenderNo: String): MigrationCreateCourtCases {
    val nomisCourtCases = courtSentencingNomisApiService.getCourtCasesForMigration(offenderNo = offenderNo)
    val dpsCases = nomisCourtCases.map { it.toMigrationDpsCourtCase() }
    return MigrationCreateCourtCases(
      prisonerId = offenderNo,
      courtCases = dpsCases,
    )
  }

  override fun parseContextFilter(json: String): MigrationMessage<*, Any> = jsonMapper.readValue(json)

  override fun parseContextPageFilter(json: String): MigrationMessage<*, MigrationPage<Any, ByLastId<CourtEventChargeIdResponse>>> = jsonMapper.readValue(json)

  override fun parseContextMapping(json: String): MigrationMessage<*, Any> = jsonMapper.readValue(json)

  override fun parseContextNomisId(json: String): MigrationMessage<*, CourtEventChargeIdResponse> = jsonMapper.readValue(json)
}
