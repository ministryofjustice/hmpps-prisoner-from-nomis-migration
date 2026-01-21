package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.MigrationCreateChargeResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.MigrationCreateCourtAppearanceResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.MigrationCreateCourtCaseResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.MigrationCreateCourtCases
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.MigrationCreateCourtCasesResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.MigrationCreatePeriodLengthResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.MigrationCreateSentenceResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtAppearanceMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtCaseBatchMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtCaseMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtChargeMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.SentenceMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.SentenceTermMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.ByPageNumber
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.ByPageNumberMigrationService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationPage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.durationMinutes

@Service
class CourtSentencingMigrationService(
  private val courtSentencingNomisApiService: CourtSentencingNomisApiService,
  private val nomisApiService: NomisApiService,
  private val courtSentencingMappingService: CourtSentencingMappingApiService,
  private val courtSentencingDpsService: CourtSentencingDpsApiService,
  jsonMapper: JsonMapper,
  @Value("\${courtsentencing.page.size:1000}") pageSize: Long,
  @Value("\${complete-check.delay-seconds}") completeCheckDelaySeconds: Int,
  @Value("\${complete-check.count}") completeCheckCount: Int,
) : ByPageNumberMigrationService<CourtSentencingMigrationFilter, PrisonerId, CourtCaseMigrationMapping>(
  mappingService = courtSentencingMappingService,
  migrationType = MigrationType.COURT_SENTENCING,
  pageSize = pageSize,
  completeCheckDelaySeconds = completeCheckDelaySeconds,
  completeCheckCount = completeCheckCount,
  jsonMapper = jsonMapper,
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun getIds(
    migrationFilter: CourtSentencingMigrationFilter,
    pageSize: Long,
    pageNumber: Long,
  ): PageImpl<PrisonerId> = if (migrationFilter.offenderNo.isNullOrEmpty()) {
    nomisApiService.getPrisonerIds(
      pageNumber = pageNumber,
      pageSize = pageSize,
    )
  } else {
    // If a single prisoner migration is requested then we'll trust the input as we're probably testing. Pretend that we called nomis-prisoner-api which found a single prisoner.
    val offenderNoList = migrationFilter.offenderNo.split(",").map { PrisonerId(it) }
    PageImpl<PrisonerId>(offenderNoList, Pageable.ofSize(1), 1)
  }

  override suspend fun getPageOfIds(
    migrationFilter: CourtSentencingMigrationFilter,
    pageSize: Long,
    pageNumber: Long,
  ): List<PrisonerId> = getIds(migrationFilter, pageSize, pageNumber).content

  override suspend fun getTotalNumberOfIds(migrationFilter: CourtSentencingMigrationFilter): Long = getIds(migrationFilter, 1, 0).totalElements

  override suspend fun getContextProperties(migrationFilter: CourtSentencingMigrationFilter): MutableMap<String, Any> = mutableMapOf("deleteExisting" to migrationFilter.deleteExisting)

  override suspend fun migrateNomisEntity(context: MigrationContext<PrisonerId>) {
    synchronisePrisonerCases(
      offenderNo = context.body.offenderNo,
      deleteExisting = context.properties["deleteExisting"] == true,
      context = context,
    ) {
      telemetryClient.trackEvent(
        "court-sentencing-migration-entity-migrated",
        mapOf(
          "offenderNo" to context.body.offenderNo,
          "migrationId" to context.migrationId,
        ),
        null,
      )
    }
  }

  suspend fun synchronisePrisonerCases(offenderNo: String, deleteExisting: Boolean, context: MigrationContext<PrisonerId>, onSuccess: () -> Unit) {
    val nomisCourtCases = courtSentencingNomisApiService.getCourtCasesForMigration(offenderNo = offenderNo)
    val dpsCases = nomisCourtCases.map { it.toMigrationDpsCourtCase() }
    courtSentencingDpsService.createCourtCaseMigration(
      MigrationCreateCourtCases(
        prisonerId = offenderNo,
        courtCases = dpsCases,
      ),
      deleteExisting = deleteExisting,
    )
      .also { dpsCourtCaseCreateResponse ->
        createMigrationMapping(
          offenderNo = offenderNo,
          dpsCourtCasesCreateResponse = dpsCourtCaseCreateResponse,
          context,
        )
        onSuccess()
      }
  }

  override suspend fun retryCreateMapping(context: MigrationContext<CourtCaseMigrationMapping>) {
    courtSentencingMappingService.createMapping(
      offenderNo = context.body.offenderNo,
      context.body.mapping,
      object : ParameterizedTypeReference<DuplicateErrorResponse<CourtCaseBatchMappingDto>>() {},
    ).also {
      if (it.isError) {
        telemetryClient.trackEvent(
          "nomis-migration-court-sentencing-duplicate",
          mapOf<String, String>(
            "migrationId" to context.migrationId,
            "offenderId" to context.body.offenderNo,
            "durationMinutes" to context.durationMinutes().toString(),
          ),
          null,
        )
      }
    }
  }

  private suspend fun createMigrationMapping(
    offenderNo: String,
    dpsCourtCasesCreateResponse: MigrationCreateCourtCasesResponse,
    context: MigrationContext<*>,
  ) {
    val mapping = CourtCaseBatchMappingDto(
      courtCases = buildCourtCaseMapping(dpsCourtCasesCreateResponse.courtCases),
      courtCharges = buildCourtChargeMapping(dpsCourtCasesCreateResponse.charges),
      courtAppearances = buildCourtAppearanceMapping(dpsCourtCasesCreateResponse.appearances),
      sentences = buildSentenceMapping(dpsCourtCasesCreateResponse.sentences),
      sentenceTerms = buildSentenceTermMapping(dpsCourtCasesCreateResponse.sentenceTerms),
      label = context.migrationId,
      mappingType = CourtCaseBatchMappingDto.MappingType.MIGRATED,
    )

    try {
      courtSentencingMappingService.createMapping(
        offenderNo = offenderNo,
        mapping,
        object : ParameterizedTypeReference<DuplicateErrorResponse<CourtCaseBatchMappingDto>>() {},
      ).also {
        if (it.isError) {
          telemetryClient.trackEvent(
            "nomis-migration-court-sentencing-duplicate",
            mapOf<String, String>(
              "migrationId" to context.migrationId,
              "offenderId" to offenderNo,
              "durationMinutes" to context.durationMinutes().toString(),
            ),
            null,
          )
        }
      }
    } catch (e: Exception) {
      log.error(
        "Failed to create mapping for Offender No: $offenderNo",
        e,
      )
      queueService.sendMessage(
        MigrationMessageType.RETRY_MIGRATION_MAPPING,
        MigrationContext(
          context = context,
          body = CourtCaseMigrationMapping(offenderNo = offenderNo, mapping = mapping),
        ),
      )
    }
  }

  // dependent on court appearance order back from dps to match nomis
  private fun buildCourtAppearanceMapping(responseMappings: List<MigrationCreateCourtAppearanceResponse>): List<CourtAppearanceMappingDto> = responseMappings.map {
    CourtAppearanceMappingDto(
      nomisCourtAppearanceId = it.eventId,
      dpsCourtAppearanceId = it.appearanceUuid.toString(),
      mappingType = CourtAppearanceMappingDto.MappingType.MIGRATED,
    )
  }

  private fun buildCourtChargeMapping(responseMappings: List<MigrationCreateChargeResponse>): List<CourtChargeMappingDto> = responseMappings.map {
    CourtChargeMappingDto(
      nomisCourtChargeId = it.chargeNOMISId,
      dpsCourtChargeId = it.chargeUuid.toString(),
      mappingType = CourtChargeMappingDto.MappingType.MIGRATED,
    )
  }

  private fun buildSentenceMapping(responseMappings: List<MigrationCreateSentenceResponse>): List<SentenceMappingDto> = responseMappings.map {
    SentenceMappingDto(
      nomisSentenceSequence = it.sentenceNOMISId.sequence,
      nomisBookingId = it.sentenceNOMISId.offenderBookingId,
      dpsSentenceId = it.sentenceUuid.toString(),
      mappingType = SentenceMappingDto.MappingType.MIGRATED,
    )
  }

  private fun buildSentenceTermMapping(responseMappings: List<MigrationCreatePeriodLengthResponse>): List<SentenceTermMappingDto> = responseMappings.map {
    SentenceTermMappingDto(
      nomisSentenceSequence = it.sentenceTermNOMISId.sentenceSequence,
      nomisBookingId = it.sentenceTermNOMISId.offenderBookingId,
      dpsTermId = it.periodLengthUuid.toString(),
      nomisTermSequence = it.sentenceTermNOMISId.termSequence,
      mappingType = SentenceTermMappingDto.MappingType.MIGRATED,
    )
  }

  private fun buildCourtCaseMapping(responseMappings: List<MigrationCreateCourtCaseResponse>): List<CourtCaseMappingDto> = responseMappings.map { CourtCaseMappingDto(nomisCourtCaseId = it.caseId, dpsCourtCaseId = it.courtCaseUuid, mappingType = CourtCaseMappingDto.MappingType.MIGRATED) }

  suspend fun offenderMigrationPayload(offenderNo: String): MigrationCreateCourtCases {
    val nomisCourtCases = courtSentencingNomisApiService.getCourtCasesForMigration(offenderNo = offenderNo)
    val dpsCases = nomisCourtCases.map { it.toMigrationDpsCourtCase() }
    return MigrationCreateCourtCases(
      prisonerId = offenderNo,
      courtCases = dpsCases,
    )
  }
  override fun parseContextFilter(json: String): MigrationMessage<*, CourtSentencingMigrationFilter> = jsonMapper.readValue(json)

  override fun parseContextPageFilter(json: String): MigrationMessage<*, MigrationPage<CourtSentencingMigrationFilter, ByPageNumber>> = jsonMapper.readValue(json)

  override fun parseContextNomisId(json: String): MigrationMessage<*, PrisonerId> = jsonMapper.readValue(json)

  override fun parseContextMapping(json: String): MigrationMessage<*, CourtCaseMigrationMapping> = jsonMapper.readValue(json)
}
