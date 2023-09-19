package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nonassociations

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.data.domain.PageImpl
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.NonAssociationMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.NonAssociationMappingDto.MappingType.MIGRATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.NonAssociationIdResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.NonAssociationResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.AuditService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationHistoryService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType.NON_ASSOCIATIONS
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.durationMinutes
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.toUpsertSyncRequest

@Service
class NonAssociationsMigrationService(
  queueService: MigrationQueueService,
  private val nomisApiService: NomisApiService,
  migrationHistoryService: MigrationHistoryService,
  telemetryClient: TelemetryClient,
  auditService: AuditService,
  private val nonAssociationsService: NonAssociationsService,
  private val nonAssociationsMappingService: NonAssociationsMappingService,
  @Value("\${non-associations.page.size:1000}") pageSize: Long,
  @Value("\${complete-check.delay-seconds}") completeCheckDelaySeconds: Int,
  @Value("\${complete-check.count}") completeCheckCount: Int,

) : MigrationService<NonAssociationsMigrationFilter, NonAssociationIdResponse, NonAssociationResponse, NonAssociationMappingDto>(
  queueService = queueService,
  auditService = auditService,
  migrationHistoryService = migrationHistoryService,
  mappingService = nonAssociationsMappingService,
  telemetryClient = telemetryClient,
  migrationType = NON_ASSOCIATIONS,
  pageSize = pageSize,
  completeCheckDelaySeconds = completeCheckDelaySeconds,
  completeCheckCount = completeCheckCount,
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  override suspend fun getIds(
    migrationFilter: NonAssociationsMigrationFilter,
    pageSize: Long,
    pageNumber: Long,
  ): PageImpl<NonAssociationIdResponse> {
    return nomisApiService.getNonAssociationIds(
      fromDate = migrationFilter.fromDate,
      toDate = migrationFilter.toDate,
      pageNumber = pageNumber,
      pageSize = pageSize,
    )
  }

  override suspend fun migrateNomisEntity(context: MigrationContext<NonAssociationIdResponse>) {
    with(context.body) {
      log.info("attempting to migrate $this")

      if (isNotPrimaryNonAssociation()) {
        telemetryClient.trackEvent(
          "non-association-migration-non-primary-skipped",
          mapOf(
            "firstOffenderNo" to offenderNo1,
            "secondOffenderNo" to offenderNo2,
            "migrationId" to context.migrationId,
          ),
        )
        return
      }

      // Determine all valid non-associations for this offender pair
      val nonAssociationPairs = nomisApiService.getNonAssociations(offenderNo1, offenderNo2)
      nonAssociationPairs.forEach { nomisNonAssociationResponse ->
        if (nomisNonAssociationResponse.isOpenAndNewestOrClosed(nonAssociationPairs.size)) {
          nonAssociationsMappingService.findNomisNonAssociationMapping(offenderNo1, offenderNo2, nomisNonAssociationResponse.typeSequence)
            ?.run {
              log.info(
                "Will not migrate the non-association since it is migrated already, NOMIS firstOffenderNo is $firstOffenderNo, " +
                  "secondOffenderNo is $secondOffenderNo, nomisTypeSequence is $nomisTypeSequence as part migration " +
                  "${this.label ?: "NONE"} (${this.mappingType})",
              )
            }
            ?: run {
              log.debug("No non-association mapping - sending non-association migrate upsert {}", nomisNonAssociationResponse.toUpsertSyncRequest())
              val migratedNonAssociation = nonAssociationsService.migrateNonAssociation(nomisNonAssociationResponse.toUpsertSyncRequest())
                .also {
                  createNonAssociationMapping(
                    nonAssociationId = it.id,
                    firstOffenderNo = offenderNo1,
                    secondOffenderNo = offenderNo2,
                    nomisTypeSequence = nomisNonAssociationResponse.typeSequence,
                    context = context,
                  )
                }
              telemetryClient.trackEvent(
                "non-associations-migration-entity-migrated",
                mapOf(
                  "nonAssociationId" to migratedNonAssociation.id.toString(),
                  "firstOffenderNo" to offenderNo1,
                  "secondOffenderNo" to offenderNo2,
                  "nomisTypeSequence" to nomisNonAssociationResponse.typeSequence.toString(),
                  "migrationId" to context.migrationId,
                ),
                null,
              )
            }
        } else {
          telemetryClient.trackEvent(
            "non-association-migration-entity-ignored",
            mapOf(
              "firstOffenderNo" to offenderNo1,
              "secondOffenderNo" to offenderNo2,
              "nomisTypeSequence" to nomisNonAssociationResponse.typeSequence.toString(),
              "migrationId" to context.migrationId,
            ),
            null,
          )
        }
      }
    }
  }

  private suspend fun createNonAssociationMapping(
    nonAssociationId: Long,
    firstOffenderNo: String,
    secondOffenderNo: String,
    nomisTypeSequence: Int,
    context: MigrationContext<*>,
  ) = try {
    nonAssociationsMappingService.createMapping(
      NonAssociationMappingDto(
        nonAssociationId = nonAssociationId,
        firstOffenderNo = firstOffenderNo,
        secondOffenderNo = secondOffenderNo,
        nomisTypeSequence = nomisTypeSequence,
        label = context.migrationId,
        mappingType = MIGRATED,
      ),
      object : ParameterizedTypeReference<DuplicateErrorResponse<NonAssociationMappingDto>>() {},
    ).also {
      if (it.isError) {
        val duplicateErrorDetails = (it.errorResponse!!).moreInfo
        telemetryClient.trackEvent(
          "nomis-migration-non-association-duplicate",
          mapOf<String, String>(
            "migrationId" to context.migrationId,
            "duplicateNonAssociationId" to duplicateErrorDetails.duplicate.nonAssociationId.toString(),
            "duplicateFirstOffenderNo" to duplicateErrorDetails.duplicate.firstOffenderNo,
            "duplicateSecondOffenderNo" to duplicateErrorDetails.duplicate.secondOffenderNo,
            "duplicateNomisTypeSequence" to duplicateErrorDetails.duplicate.nomisTypeSequence.toString(),
            "existingNonAssociationId" to duplicateErrorDetails.existing.nonAssociationId.toString(),
            "existingFirstOffenderNo" to duplicateErrorDetails.existing.firstOffenderNo,
            "existingSecondOffenderNo" to duplicateErrorDetails.existing.secondOffenderNo,
            "existingNomisTypeSequence" to duplicateErrorDetails.existing.nomisTypeSequence.toString(),
            "durationMinutes" to context.durationMinutes().toString(),
          ),
          null,
        )
      }
    }
  } catch (e: Exception) {
    log.error(
      "Failed to create mapping for nonAssociation id $nonAssociationId, firstOffenderNo $firstOffenderNo, " +
        "secondOffenderNo $secondOffenderNo, typeSequence $nomisTypeSequence",
      e,
    )
    queueService.sendMessage(
      MigrationMessageType.RETRY_MIGRATION_MAPPING,
      MigrationContext(
        context = context,
        body = NonAssociationMappingDto(
          nonAssociationId = nonAssociationId,
          firstOffenderNo = firstOffenderNo,
          secondOffenderNo = secondOffenderNo,
          nomisTypeSequence = nomisTypeSequence,
          mappingType = MIGRATED,
        ),
      ),
    )
  }
}

// Two non-association events occur for each non-association relationship - use the offenderIds to uniquely identify each pair
fun NonAssociationIdResponse.isNotPrimaryNonAssociation(): Boolean = offenderNo1 > offenderNo2

fun NonAssociationResponse.isOpenAndNewestOrClosed(nonAssociationPairCount: Int) =
  typeSequence == nonAssociationPairCount || expiryDate != null
