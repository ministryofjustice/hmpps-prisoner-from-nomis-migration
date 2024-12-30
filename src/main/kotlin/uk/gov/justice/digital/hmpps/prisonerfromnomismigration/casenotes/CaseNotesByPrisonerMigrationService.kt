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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.CreateMappingResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CaseNoteMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CaseNoteMappingIdDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PrisonerCaseNoteMappingsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CaseNoteResponse
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
) : MigrationService<CaseNotesMigrationFilter, PrisonerId, CaseNoteMigrationMapping>(
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
    val offenderNo = context.body.offenderNo
    log.info("attempting to migrate $offenderNo")
    val telemetry = mutableMapOf(
      "offenderNo" to offenderNo,
      "migrationId" to context.migrationId,
      "migrationType" to "CASENOTES",
    )
    try {
      val prisonerCaseNotesResponse = (
        caseNotesNomisService.getCaseNotesForPrisonerOrNull(offenderNo)
          ?: PrisonerCaseNotesResponse(emptyList())
        )
      val nomisCaseNotes = prisonerCaseNotesResponse
        .caseNotes
        .deDuplicate()
      val numberOfDupes = prisonerCaseNotesResponse.caseNotes.size - nomisCaseNotes.size
      telemetry["numberOfDupes"] = numberOfDupes.toString()

      // Edge cases: there could be exact duplicates of case notes, and/or merged case note copies.
      // If a particular case note has n duplicates (ie there are n in all), only one of them will be migrated, but there will
      // in general be n * m identical MERGE copies, where m is the number of merges that have occurred.
      // The merge copies will be mapped to the one that is migrated.
      // This is ok as a change in DPS will be reflected in all the copies.

      val originalNomisIdToCopiesMap = mutableMapOf<Long, MutableList<CaseNoteResponse>>()
      val copiedNomisIdSet = mutableSetOf<Long>()

      // look for the originals of the merge copies under another booking id

      nomisCaseNotes
        .filter { it.auditModuleName == "MERGE" }
        .forEach { mergeCopiedCaseNote ->
          val originals = nomisCaseNotes.filter {
            isMergeCopy(it, mergeCopiedCaseNote)
          }
          if (originals.isEmpty()) {
            // There are 8226 of these (as at dec 2024), they are treated as not merged but ordinary case notes
            log.warn("No original found for offenderNo: $offenderNo, merged case note ${mergeCopiedCaseNote.caseNoteId}")
          } else if (originals.size > 1) {
            // Should not be possible given that duplicates were filtered out
            throw IllegalStateException("${originals.size} originals found for offenderNo: $offenderNo, merged case note ${mergeCopiedCaseNote.caseNoteId}")
          } else {
            copiedNomisIdSet.add(mergeCopiedCaseNote.caseNoteId)
          }

          originals.firstOrNull()
            ?.let {
              originalNomisIdToCopiesMap.getOrPut(it.caseNoteId) { mutableListOf() }
                .add(mergeCopiedCaseNote)
            }
        }

      val caseNotesToMigrate = nomisCaseNotes
        .filterNot { copiedNomisIdSet.contains(it.caseNoteId) }
        .map { it.toDPSCreateCaseNote(offenderNo) }

      val bookingIdMap: Map<Long, Long> = nomisCaseNotes.associate { it.caseNoteId to it.bookingId }

      caseNotesDpsService.migrateCaseNotes(offenderNo, caseNotesToMigrate)
        .also { migrationResultList ->
          createMapping(
            offenderNo = offenderNo,
            PrisonerCaseNoteMappingsDto(
              label = context.migrationId,
              mappingType = PrisonerCaseNoteMappingsDto.MappingType.MIGRATED,
              mappings = migrationResultList.map { migrationResult ->
                CaseNoteMappingIdDto(
                  nomisBookingId = bookingIdMap[migrationResult.legacyId] ?: 0,
                  nomisCaseNoteId = migrationResult.legacyId,
                  dpsCaseNoteId = migrationResult.id.toString(),
                )
              },
            ),
            context = context,
          )

          if (originalNomisIdToCopiesMap.isNotEmpty()) {
            // Additional mappings for dps uuids to nomis case notes which are copies (MERGE)
            caseNotesMappingService.createMappings(
              migrationResultList
                .filter { m -> originalNomisIdToCopiesMap.containsKey(m.legacyId) }
                .flatMap { original ->
                  originalNomisIdToCopiesMap[original.legacyId]!!
                    .map { mergeCopiedCaseNote ->
                      CaseNoteMappingDto(
                        nomisBookingId = mergeCopiedCaseNote.bookingId,
                        nomisCaseNoteId = mergeCopiedCaseNote.caseNoteId,
                        dpsCaseNoteId = original.id.toString(),
                        offenderNo = offenderNo,
                        mappingType = CaseNoteMappingDto.MappingType.MIGRATED,
                        label = context.migrationId,
                      )
                    }
                },
              object : ParameterizedTypeReference<DuplicateErrorResponse<CaseNoteMappingDto>>() {},
            ).also {
              checkForDuplicateError(it, context)
            }
          }
          telemetry["caseNoteCount"] = migrationResultList.size.toString()
          telemetryClient.trackEvent("casenotes-migration-entity-migrated", telemetry)
        }
    } catch (e: Exception) {
      telemetry["error"] = e.message ?: "unknown error"
      telemetryClient.trackEvent("casenotes-migration-entity-failed", telemetry)
      throw e
    }
  }

  suspend fun createMapping(
    offenderNo: String,
    prisonerMappings: PrisonerCaseNoteMappingsDto,
    context: MigrationContext<PrisonerId>,
  ) = try {
    caseNotesMappingService.createMapping(
      offenderNo,
      prisonerMappings,
      object : ParameterizedTypeReference<DuplicateErrorResponse<CaseNoteMappingDto>>() {},
    ).also {
      checkForDuplicateError(it, context)
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

  private fun checkForDuplicateError(
    result: CreateMappingResult<CaseNoteMappingDto>,
    context: MigrationContext<PrisonerId>,
  ) {
    if (result.isError) {
      val duplicateErrorDetails = (result.errorResponse!!).moreInfo
      telemetryClient.trackEvent(
        "casenotes-migration-duplicate-error",
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
      )
    }
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

/**
 * Filter out duplicates from the list of case notes
 * This is where e.g. the user has entered identical case note details twice or more.
 * These duplicates will not be migrated
 */
fun List<CaseNoteResponse>.deDuplicate(): List<CaseNoteResponse> =
  distinctBy { caseNote ->
    listOf(
      caseNote.creationDateTime,
      caseNote.caseNoteText,
      caseNote.occurrenceDateTime,
      caseNote.caseNoteType,
      caseNote.caseNoteSubType,
      caseNote.authorStaffId,
      // Do not conflate merge copy case notes
      if (caseNote.auditModuleName == "MERGE") {
        caseNote.caseNoteId
      } else {
        0
      },
    )
  }

fun isMergeCopy(
  response: CaseNoteResponse,
  mergeCopiedCaseNote: CaseNoteResponse,
): Boolean = response.creationDateTime == mergeCopiedCaseNote.creationDateTime &&
  response.caseNoteText == mergeCopiedCaseNote.caseNoteText &&
  response.bookingId != mergeCopiedCaseNote.bookingId &&
  response.auditModuleName != "MERGE" &&
  response.caseNoteType == mergeCopiedCaseNote.caseNoteType &&
  response.caseNoteSubType == mergeCopiedCaseNote.caseNoteSubType &&
  response.occurrenceDateTime == mergeCopiedCaseNote.occurrenceDateTime &&
  response.authorStaffId == mergeCopiedCaseNote.authorStaffId
