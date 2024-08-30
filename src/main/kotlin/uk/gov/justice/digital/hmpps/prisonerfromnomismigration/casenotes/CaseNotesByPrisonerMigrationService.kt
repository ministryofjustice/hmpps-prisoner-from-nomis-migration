package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CaseNoteMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CaseNoteMappingIdDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PrisonerCaseNoteMappingsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.PrisonerCaseNotesResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.PrisonerId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.durationMinutes

@Service
class CaseNotesByPrisonerMigrationService(
  private val nomisService: NomisApiService,
  private val caseNotesNomisService: CaseNotesNomisApiService,
  private val caseNotesMappingService: CaseNotesByPrisonerMigrationMappingApiService,
  private val caseNotesDpsService: CaseNotesApiService,
  @Value("\${casenotes.page.size:1000}") pageSize: Long,
  @Value("\${complete-check.delay-seconds}") completeCheckDelaySeconds: Int,
  @Value("\${complete-check.count}") completeCheckCount: Int,
) : MigrationService<CaseNotesMigrationFilter, PrisonerId, CaseNotesForPrisonerResponse, CaseNoteMigrationMapping>(
  mappingService = caseNotesMappingService,
  migrationType = MigrationType.CASENOTES,
  pageSize = pageSize,
  completeCheckDelaySeconds = completeCheckDelaySeconds,
  completeCheckCount = completeCheckCount,
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  override suspend fun getIds(
    migrationFilter: CaseNotesMigrationFilter,
    pageSize: Long,
    pageNumber: Long,
  ): PageImpl<PrisonerId> =
    migrationFilter.offenderNos?.let {
      PageImpl<PrisonerId>(
        migrationFilter.offenderNos.map { PrisonerId(it) },
        Pageable.ofSize(migrationFilter.offenderNos.size),
        migrationFilter.offenderNos.size.toLong(),
      )
    }
      ?: nomisService.getPrisonerIds(
        pageNumber = pageNumber,
        pageSize = pageSize,
      )

  override suspend fun migrateNomisEntity(context: MigrationContext<PrisonerId>) {
    log.info("attempting to migrate ${context.body}")
    val offenderNo = context.body.offenderNo

    val nomisCaseNotes =
      caseNotesNomisService.getCaseNotesToMigrate(offenderNo) ?: PrisonerCaseNotesResponse(emptyList())
    val caseNotesToMigrate = nomisCaseNotes.caseNotes.map { it.toDPSCreateCaseNote() }
    caseNotesDpsService.migrateCaseNotes(
      offenderNo = offenderNo,
      dpsCaseNotes = caseNotesToMigrate,
    ).also {
      val zip = it.zip(nomisCaseNotes.caseNotes)
      createMapping(
        offenderNo = offenderNo,
        PrisonerCaseNoteMappingsDto(
          label = context.migrationId,
          mappingType = PrisonerCaseNoteMappingsDto.MappingType.MIGRATED,
          mappings = zip.map { (dpsCaseNote, nomisCaseNote) ->
            CaseNoteMappingIdDto(
              nomisBookingId = nomisCaseNote.bookingId,
              nomisCaseNoteId = nomisCaseNote.caseNoteId,
              dpsCaseNoteId = dpsCaseNote.caseNoteId!!,
            )
          },
        ),
        context = context,
      )
      telemetryClient.trackEvent(
        "casenotes-migration-entity-migrated",
        mapOf(
          "offenderNo" to offenderNo,
          "migrationId" to context.migrationId,
          "caseNoteCount" to it.size.toString(),
        ),
      )
    }
  }

  private suspend fun createMapping(
    offenderNo: String,
    prisonerMappings: PrisonerCaseNoteMappingsDto,
    context: MigrationContext<PrisonerId>,
  ) = try {
    caseNotesMappingService.createMapping(
      offenderNo,
      prisonerMappings,
      object : ParameterizedTypeReference<DuplicateErrorResponse<CaseNoteMappingDto>>() {},
    ).also {
      if (it.isError) {
        val duplicateErrorDetails = (it.errorResponse!!).moreInfo
        telemetryClient.trackEvent(
          "nomis-migration-casenotes-duplicate",
          mapOf<String, String>(
            "offenderNo" to context.body.offenderNo,
            "migrationId" to context.migrationId,
            "duplicateDpsCaseNoteId" to duplicateErrorDetails.duplicate.dpsCaseNoteId,
            "duplicateNomisBookingId" to duplicateErrorDetails.duplicate.nomisBookingId.toString(),
            "duplicateNomisCaseNoteId" to duplicateErrorDetails.duplicate.nomisCaseNoteId.toString(),
            "existingDpsCaseNoteId" to duplicateErrorDetails.existing.dpsCaseNoteId,
            "existingNomisBookingId" to duplicateErrorDetails.existing.nomisBookingId.toString(),
            "existingNomisCaseNoteId" to duplicateErrorDetails.existing.nomisCaseNoteId.toString(),
            "durationMinutes" to context.durationMinutes().toString(),
          ),
          null,
        )
      }
    }
  } catch (e: Exception) {
    log.error(
      "Failed to create mapping for casenotes for prisoner ${context.body.offenderNo}",
      e,
    )
    queueService.sendMessage(
      MigrationMessageType.RETRY_MIGRATION_MAPPING,
      MigrationContext(
        context = context,
        body = CaseNoteMigrationMapping(prisonerMappings, offenderNo = offenderNo),
      ),
    )
  }

  override suspend fun retryCreateMapping(context: MigrationContext<CaseNoteMigrationMapping>) {
    caseNotesMappingService.createMapping(
      context.body.offenderNo,
      context.body.prisonerMappings,
      object : ParameterizedTypeReference<DuplicateErrorResponse<CaseNoteMappingDto>>() {},
    )
  }
}

data class CaseNoteMigrationMapping(
  val prisonerMappings: PrisonerCaseNoteMappingsDto,
  val offenderNo: String,
)
