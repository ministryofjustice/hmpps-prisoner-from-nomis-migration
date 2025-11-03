package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.data.domain.PageImpl
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PrisonerRestrictionMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.NomisAudit
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerRestriction
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerRestrictionIdResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.MigratePrisonerRestrictionRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.PrisonerRestrictionMigrationResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType

@Service
class PrisonerRestrictionMigrationService(
  val prisonerRestrictionMappingService: PrisonerRestrictionMappingApiService,
  val nomisApiService: ContactPersonNomisApiService,
  val dpsApiService: ContactPersonDpsApiService,
  @Value("\${personalrelationships.page.size:1000}") pageSize: Long,
  @Value("\${personalrelationships.complete-check.delay-seconds}") completeCheckDelaySeconds: Int,
  @Value("\${personalrelationships.complete-check.retry-seconds:1}") completeCheckRetrySeconds: Int,
  @Value("\${personalrelationships.complete-check.count}") completeCheckCount: Int,
  @Value("\${complete-check.scheduled-retry-seconds}") completeCheckScheduledRetrySeconds: Int,
) : MigrationService<PrisonerRestrictionMigrationFilter, PrisonerRestrictionIdResponse, PrisonerRestrictionMappingDto>(
  mappingService = prisonerRestrictionMappingService,
  migrationType = MigrationType.PERSONALRELATIONSHIPS,
  pageSize = pageSize,
  completeCheckDelaySeconds = completeCheckDelaySeconds,
  completeCheckCount = completeCheckCount,
  completeCheckRetrySeconds = completeCheckRetrySeconds,
  completeCheckScheduledRetrySeconds = completeCheckScheduledRetrySeconds,
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun getIds(
    migrationFilter: PrisonerRestrictionMigrationFilter,
    pageSize: Long,
    pageNumber: Long,
  ): PageImpl<PrisonerRestrictionIdResponse> = nomisApiService.getPrisonerRestrictionIdsToMigrate(
    fromDate = migrationFilter.fromDate,
    toDate = migrationFilter.toDate,
    pageNumber = pageNumber,
    pageSize = pageSize,
  )

  override suspend fun getPageOfIds(
    migrationFilter: PrisonerRestrictionMigrationFilter,
    pageSize: Long,
    pageNumber: Long,
  ): List<PrisonerRestrictionIdResponse> = getIds(migrationFilter, pageSize, pageNumber).content

  override suspend fun getTotalNumberOfIds(migrationFilter: PrisonerRestrictionMigrationFilter): Long = getIds(migrationFilter, 1, 0).totalElements

  override suspend fun migrateNomisEntity(context: MigrationContext<PrisonerRestrictionIdResponse>) {
    val nomisPrisonerRestrictionId = context.body.restrictionId
    val alreadyMigratedMapping = prisonerRestrictionMappingService.getByNomisPrisonerRestrictionIdOrNull(nomisPrisonerRestrictionId)

    alreadyMigratedMapping?.run {
      log.info("Will not migrate the nomis prisoner restriction=$nomisPrisonerRestrictionId since it was already mapped to DPS contact ${this.dpsId} during migration ${this.label}")
    } ?: run {
      val restriction = nomisApiService.getPrisonerRestrictionById(prisonerRestrictionId = nomisPrisonerRestrictionId)
      val mapping = dpsApiService.migratePrisonerRestriction(
        offenderNo = restriction.offenderNo,
        restriction.toDpsMigratePrisonerRestrictionRequest(),
      ).toPrisonerRestrictionMappingsDto(
        migrationId = context.migrationId,
        nomisPrisonerRestrictionId = nomisPrisonerRestrictionId,
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

  override suspend fun retryCreateMapping(context: MigrationContext<PrisonerRestrictionMappingDto>) = createMappingOrOnFailureDo(context, context.body) {
    throw it
  }

  suspend fun createMappingOrOnFailureDo(
    context: MigrationContext<*>,
    mapping: PrisonerRestrictionMappingDto,
    failureHandler: suspend (error: Throwable) -> Unit,
  ) {
    runCatching {
      prisonerRestrictionMappingService.createMapping(mapping, object : ParameterizedTypeReference<DuplicateErrorResponse<PrisonerRestrictionMappingDto>>() {})
    }.onFailure {
      failureHandler(it)
    }.onSuccess {
      if (it.isError) {
        val duplicateErrorDetails = it.errorResponse!!.moreInfo
        telemetryClient.trackEvent(
          "nomis-migration-contactperson-duplicate",
          mapOf(
            "offenderNo" to mapping.offenderNo,
            "dpsId" to mapping.dpsId,
            "nomisId" to mapping.nomisId,
            "duplicateDpsId" to duplicateErrorDetails.duplicate.dpsId,
            "duplicateNomisId" to duplicateErrorDetails.duplicate.nomisId,
            "existingDpsId" to duplicateErrorDetails.existing.dpsId,
            "existingNomisId" to duplicateErrorDetails.existing.nomisId,
            "migrationId" to context.migrationId,
          ),
        )
      } else {
        telemetryClient.trackEvent(
          "contactperson-migration-entity-migrated",
          mapOf(
            "offenderNo" to mapping.offenderNo,
            "nomisId" to mapping.nomisId,
            "dpsId" to mapping.dpsId,
            "migrationId" to context.migrationId,
          ),
        )
      }
    }
  }
}

private fun PrisonerRestrictionMigrationResponse.toPrisonerRestrictionMappingsDto(migrationId: String, nomisPrisonerRestrictionId: Long) = PrisonerRestrictionMappingDto(
  mappingType = PrisonerRestrictionMappingDto.MappingType.MIGRATED,
  label = migrationId,
  dpsId = this.prisonerRestrictionId.toString(),
  offenderNo = this.prisonerNumber,
  nomisId = nomisPrisonerRestrictionId,
)

fun PrisonerRestriction.toDpsMigratePrisonerRestrictionRequest(): MigratePrisonerRestrictionRequest = MigratePrisonerRestrictionRequest(
  restrictionType = this.type.code,
  effectiveDate = this.effectiveDate,
  authorisedUsername = this.authorisedStaff.username,
  currentTerm = this.bookingSequence == 1L,
  expiryDate = this.expiryDate,
  commentText = this.comment,
  createdTime = this.audit.createDatetime,
  createdBy = if (this.audit.hasBeenModified()) {
    this.audit.createUsername
  } else {
    this.enteredStaff.username
  },
  updatedTime = this.audit.modifyDatetime,
  updatedBy = if (this.audit.hasBeenModified()) {
    this.enteredStaff.username
  } else {
    this.audit.modifyUserId
  },
)
private fun NomisAudit.hasBeenModified() = this.modifyUserId != null
