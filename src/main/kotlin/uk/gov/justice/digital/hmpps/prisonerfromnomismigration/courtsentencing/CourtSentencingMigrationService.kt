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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CourtEventChargeIdResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.PaginatedSimpleMigrationService

@Service
class CourtSentencingMigrationService(
  @Qualifier("mappingApiWebClient") mappingApiWebClient: WebClient,
  private val courtSentencingNomisApiService: CourtSentencingNomisApiService,
  private val courtSentencingMappingService: CourtSentencingMappingApiService,
  private val courtSentencingDpsService: CourtSentencingDpsApiService,
  jsonMapper: JsonMapper,
  @Value("\${courtsentencing.page.size:1000}") pageSize: Long,
  @Value("\${complete-check.delay-seconds}") completeCheckDelaySeconds: Int,
  @Value("\${complete-check.count}") completeCheckCount: Int,
) : PaginatedSimpleMigrationService<CourtEventChargeIdResponse>(
  mappingApiWebClient = mappingApiWebClient,
  migrationType = MigrationType.COURT_SENTENCING,
  pageSize = pageSize,
  completeCheckDelaySeconds = completeCheckDelaySeconds,
  completeCheckCount = completeCheckCount,
  jsonMapper = jsonMapper,
) {

  override suspend fun getIds(pageSize: Long, pageNumber: Long): List<CourtEventChargeIdResponse> = courtSentencingNomisApiService.getCourtEventChargeIdsWithPropertyValue(
    page = pageNumber.toInt(),
    size = pageSize.toInt(),
  ).content ?: emptyList()

  override suspend fun getTotalCount(): Long = courtSentencingNomisApiService.getCourtEventChargeIdsWithPropertyValue(page = 0, size = 1).totalElements ?: 0

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
    courtSentencingDpsService.updateCourtCharge(
      chargeId = chargeMapping.dpsCourtChargeId,
      appearanceId = appearanceMapping.dpsCourtAppearanceId,
      charge = nomisCharge.toDpsCharge(),
    )
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

  override fun parseContextNomisId(json: String): MigrationMessage<*, CourtEventChargeIdResponse> = jsonMapper.readValue(json)
}
