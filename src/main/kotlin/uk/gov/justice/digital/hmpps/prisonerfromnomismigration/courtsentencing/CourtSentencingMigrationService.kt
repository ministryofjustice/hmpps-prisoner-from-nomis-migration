package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.MigrationCreateChargeResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.MigrationCreateCourtAppearanceResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.MigrationCreateCourtCaseResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.MigrationCreateCourtCases
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.MigrationCreateCourtCasesResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.MigrationCreateSentenceResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtAppearanceMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtCaseMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtCaseMigrationMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtChargeMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.SentenceMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.durationMinutes

@Service
class CourtSentencingMigrationService(
  private val courtSentencingNomisApiService: CourtSentencingNomisApiService,
  private val nomisApiService: NomisApiService,
  private val courtSentencingMappingService: CourtSentencingMappingApiService,
  private val courtSentencingDpsService: CourtSentencingDpsApiService,
  @Value("\${courtsentencing.page.size:1000}") pageSize: Long,
  @Value("\${complete-check.delay-seconds}") completeCheckDelaySeconds: Int,
  @Value("\${complete-check.count}") completeCheckCount: Int,
) : MigrationService<CourtSentencingMigrationFilter, PrisonerId, CourtCaseMigrationMapping>(
  mappingService = courtSentencingMappingService,
  migrationType = MigrationType.COURT_SENTENCING,
  pageSize = pageSize,
  completeCheckDelaySeconds = completeCheckDelaySeconds,
  completeCheckCount = completeCheckCount,
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  override suspend fun getIds(
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
    PageImpl<PrisonerId>(mutableListOf(PrisonerId(migrationFilter.offenderNo)), Pageable.ofSize(1), 1)
  }

  override suspend fun migrateNomisEntity(context: MigrationContext<PrisonerId>) {
    val offenderNo = context.body.offenderNo
    val nomisCourtCases = courtSentencingNomisApiService.getCourtCasesForMigration(offenderNo = offenderNo)
    val dpsCases = nomisCourtCases.map { it.toMigrationDpsCourtCase(nomisCourtCases.findLinkedCaseOrNull(it)) }
    // idempotent migration - will migrate without checking for existing migration
    courtSentencingDpsService.createCourtCaseMigration(
      MigrationCreateCourtCases(
        prisonerId = offenderNo,
        courtCases = dpsCases,
      ),
    )
      .also { dpsCourtCaseCreateResponse ->
        createMigrationMapping(
          offenderNo = offenderNo,
          dpsCourtCasesCreateResponse = dpsCourtCaseCreateResponse,
          context,
        )
        telemetryClient.trackEvent(
          "court-sentencing-migration-entity-migrated",
          mapOf(
            "offenderNo" to offenderNo,
            "migrationId" to context.migrationId,
          ),
          null,
        )
      }
  }

  override suspend fun retryCreateMapping(context: MigrationContext<CourtCaseMigrationMapping>) {
    courtSentencingMappingService.createMapping(
      offenderNo = context.body.offenderNo,
      context.body.mapping,
      object : ParameterizedTypeReference<DuplicateErrorResponse<CourtCaseMigrationMappingDto>>() {},
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
    val mapping = CourtCaseMigrationMappingDto(
      courtCases = buildCourtCaseMapping(dpsCourtCasesCreateResponse.courtCases),
      courtCharges = buildCourtChargeMapping(dpsCourtCasesCreateResponse.charges),
      courtAppearances = buildCourtAppearanceMapping(dpsCourtCasesCreateResponse.appearances),
      sentences = buildSentenceMapping(dpsCourtCasesCreateResponse.sentences),
      label = context.migrationId,
      mappingType = CourtCaseMigrationMappingDto.MappingType.MIGRATED,
    )

    try {
      courtSentencingMappingService.createMapping(
        offenderNo = offenderNo,
        mapping,
        object : ParameterizedTypeReference<DuplicateErrorResponse<CourtCaseMigrationMappingDto>>() {},
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
  private fun buildCourtAppearanceMapping(responseMappings: List<MigrationCreateCourtAppearanceResponse>): List<CourtAppearanceMappingDto> = responseMappings.map { it ->
    CourtAppearanceMappingDto(
      nomisCourtAppearanceId = it.eventId.toLong(),
      dpsCourtAppearanceId = it.appearanceUuid.toString(),
    )
  }

  private fun buildCourtChargeMapping(responseMappings: List<MigrationCreateChargeResponse>): List<CourtChargeMappingDto> = responseMappings.map { it ->
    CourtChargeMappingDto(
      nomisCourtChargeId = it.chargeNOMISId.toLong(),
      dpsCourtChargeId = it.chargeUuid.toString(),
    )
  }

  private fun buildSentenceMapping(responseMappings: List<MigrationCreateSentenceResponse>): List<SentenceMappingDto> = responseMappings.map { it ->
    SentenceMappingDto(
      nomisSentenceSequence = it.sentenceNOMISId.sequence,
      nomisBookingId = it.sentenceNOMISId.offenderBookingId,
      dpsSentenceId = it.sentenceUuid.toString(),
    )
  }

  private fun buildCourtCaseMapping(responseMappings: List<MigrationCreateCourtCaseResponse>): List<CourtCaseMappingDto> = responseMappings.map { it -> CourtCaseMappingDto(nomisCourtCaseId = it.caseId, dpsCourtCaseId = it.courtCaseUuid) }
}
