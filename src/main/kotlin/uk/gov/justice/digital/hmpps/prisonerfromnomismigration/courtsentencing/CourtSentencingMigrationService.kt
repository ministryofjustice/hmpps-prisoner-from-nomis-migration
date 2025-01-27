package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.data.domain.PageImpl
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.MigrationCreateChargeResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.MigrationCreateCourtAppearanceResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.MigrationCreateCourtCaseResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtAppearanceMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtCaseAllMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtChargeMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CourtCaseIdResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CourtCaseResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.durationMinutes

@Service
class CourtSentencingMigrationService(
  private val nomisApiService: CourtSentencingNomisApiService,
  private val courtSentencingMappingService: CourtSentencingMappingApiService,
  private val courtSentencingDpsService: CourtSentencingDpsApiService,
  @Value("\${courtsentencing.page.size:1000}") pageSize: Long,
  @Value("\${complete-check.delay-seconds}") completeCheckDelaySeconds: Int,
  @Value("\${complete-check.count}") completeCheckCount: Int,
) : MigrationService<CourtSentencingMigrationFilter, CourtCaseIdResponse, CourtCaseAllMappingDto>(
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
  ): PageImpl<CourtCaseIdResponse> = nomisApiService.getCourtCaseIds(
    fromDate = migrationFilter.fromDate,
    toDate = migrationFilter.toDate,
    pageNumber = pageNumber,
    pageSize = pageSize,
  )

  // TODO waiting for DPS new or amended endpoint to return created ids for appearances and charges
  // TODO consider Next Appearance - nomis already has a seperate apppearance for the next appearance date
  override suspend fun migrateNomisEntity(context: MigrationContext<CourtCaseIdResponse>) {
    log.info("attempting to migrate ${context.body}")
    val nomisCaseId = context.body.caseId

    courtSentencingMappingService.getCourtCaseOrNullByNomisId(
      courtCaseId = nomisCaseId,
    )?.run {
      log.info("Will not migrate the court case since it is migrated already, NOMIS caseId is $nomisCaseId as part of migration ${this.label ?: "NONE"} (${this.mappingType})")
    } ?: run {
      val nomisCourtCase =
        nomisApiService.getCourtCaseForMigration(
          courtCaseId = nomisCaseId,
        )

      if (nomisCourtCase.combinedCaseId != null) {
        log.info("Will not migrate the court case since it is the locked down part of a linked case, NOMIS caseId is $nomisCaseId")
        telemetryClient.trackEvent(
          "court-sentencing-migration-entity-skipped",
          mapOf(
            "nomisCourtCaseId" to nomisCaseId.toString(),
            "linkedCaseId" to nomisCourtCase.combinedCaseId.toString(),
            "reason" to "linked case",
            "offenderNo" to nomisCourtCase.offenderNo,
            "migrationId" to context.migrationId,
          ),
          null,
        )
      } else {
        courtSentencingDpsService.createCourtCaseMigration(nomisCourtCase.toMigrationDpsCourtCase())
          .also { dpsCourtCaseCreateResponse ->
            createCourtCaseMapping(
              nomisCourtCase = nomisCourtCase,
              dpsCourtCaseCreateResponse = dpsCourtCaseCreateResponse,
              context,
            )
            telemetryClient.trackEvent(
              "court-sentencing-migration-entity-migrated",
              mapOf(
                "nomisCourtCaseId" to nomisCaseId.toString(),
                "dpsCourtCaseId" to dpsCourtCaseCreateResponse.courtCaseUuid,
                "offenderNo" to nomisCourtCase.offenderNo,
                "migrationId" to context.migrationId,
              ),
              null,
            )
          }
      }
    }
  }

  private suspend fun createCourtCaseMapping(
    nomisCourtCase: CourtCaseResponse,
    dpsCourtCaseCreateResponse: MigrationCreateCourtCaseResponse,
    context: MigrationContext<*>,
  ) {
    val mapping = CourtCaseAllMappingDto(
      nomisCourtCaseId = nomisCourtCase.id,
      dpsCourtCaseId = dpsCourtCaseCreateResponse.courtCaseUuid,
      courtCharges = buildCourtChargeMapping(dpsCourtCaseCreateResponse.charges),
      courtAppearances = buildCourtAppearanceMapping(dpsCourtCaseCreateResponse.appearances),
      label = context.migrationId,
      mappingType = CourtCaseAllMappingDto.MappingType.MIGRATED,
    )
    try {
      courtSentencingMappingService.createMapping(
        mapping,
        object : ParameterizedTypeReference<DuplicateErrorResponse<CourtCaseAllMappingDto>>() {},
      ).also {
        if (it.isError) {
          val duplicateErrorDetails = (it.errorResponse!!).moreInfo
          telemetryClient.trackEvent(
            "nomis-migration-court-sentencing-duplicate",
            mapOf<String, String>(
              "migrationId" to context.migrationId,
              "duplicateDpsCourtCaseId" to duplicateErrorDetails.duplicate.dpsCourtCaseId,
              "duplicateNomisCourtCaseId" to duplicateErrorDetails.duplicate.nomisCourtCaseId.toString(),
              "existingDpsCourtCaseId" to duplicateErrorDetails.existing.dpsCourtCaseId,
              "existingNomisCourtCaseId" to duplicateErrorDetails.existing.nomisCourtCaseId.toString(),
              "durationMinutes" to context.durationMinutes().toString(),
            ),
            null,
          )
        }
      }
    } catch (e: Exception) {
      log.error(
        "Failed to create mapping for Court case nomis id: ${nomisCourtCase.id}, dps Court case id ${dpsCourtCaseCreateResponse.courtCaseUuid}",
        e,
      )
      queueService.sendMessage(
        MigrationMessageType.RETRY_MIGRATION_MAPPING,
        MigrationContext(
          context = context,
          body = mapping,
        ),
      )
    }
  }

  // dependent on court appearance order back from dps to match nomis
  private fun buildCourtAppearanceMapping(responseMappings: List<MigrationCreateCourtAppearanceResponse>): List<CourtAppearanceMappingDto> = responseMappings.map { it -> CourtAppearanceMappingDto(nomisCourtAppearanceId = it.eventId.toLong(), dpsCourtAppearanceId = it.lifetimeUuid.toString()) }

  private fun buildCourtChargeMapping(responseMappings: List<MigrationCreateChargeResponse>): List<CourtChargeMappingDto> = responseMappings.map { it -> CourtChargeMappingDto(nomisCourtChargeId = it.chargeNOMISId.toLong(), dpsCourtChargeId = it.lifetimeChargeUuid.toString()) }
}
