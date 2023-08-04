package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageImpl
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model.AdjudicationMigrateDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model.MigrateOffence
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model.MigratePrisoner
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model.ReportingOfficer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.AdjudicationChargeIdResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.AdjudicationChargeResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.AdjudicationResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.AuditService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationHistoryService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService

private fun AdjudicationChargeResponse.toAdjudication(): AdjudicationMigrateDto =
  AdjudicationMigrateDto(
    agencyIncidentId = 1,
    oicIncidentId = this.adjudicationNumber!!, // TODO looks like A mistake in NOMIS API swagger, this cannot be null
    offenceSequence = this.charge.chargeSequence.toLong(),
    bookingId = 1,
    agencyId = "MDI",
    incidentDateTime = "2021-01-01T12:00:00",
    locationId = 1,
    statement = "statement",
    reportingOfficer = ReportingOfficer("M.BOB"),
    createdByUsername = "J.KWEKU",
    prisoner = MigratePrisoner(prisonerNumber = "A1234KL", gender = "M", currentAgencyId = "MDI"),
    offence = MigrateOffence("51:1B"),
    victims = emptyList(),
    associates = emptyList(),
    witnesses = emptyList(),
    damages = emptyList(),
    evidence = emptyList(),
    punishments = emptyList(),
    hearings = emptyList(),
  )

@Service
class AdjudicationsMigrationService(
  queueService: MigrationQueueService,
  private val nomisApiService: NomisApiService,
  migrationHistoryService: MigrationHistoryService,
  telemetryClient: TelemetryClient,
  auditService: AuditService,
  private val adjudicationsMappingService: AdjudicationsMappingService,
  private val adjudicationsService: AdjudicationsService,
  @Value("\${adjudications.page.size:1000}") pageSize: Long,
  @Value("\${complete-check.delay-seconds}") completeCheckDelaySeconds: Int,
  @Value("\${complete-check.count}") completeCheckCount: Int,
) :
  MigrationService<AdjudicationsMigrationFilter, AdjudicationChargeIdResponse, AdjudicationResponse, AdjudicationMapping>(
    queueService = queueService,
    auditService = auditService,
    migrationHistoryService = migrationHistoryService,
    mappingService = adjudicationsMappingService,
    telemetryClient = telemetryClient,
    migrationType = MigrationType.ADJUDICATIONS,
    pageSize = pageSize,
    completeCheckDelaySeconds = completeCheckDelaySeconds,
    completeCheckCount = completeCheckCount,
  ) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  override suspend fun getIds(
    migrationFilter: AdjudicationsMigrationFilter,
    pageSize: Long,
    pageNumber: Long,
  ): PageImpl<AdjudicationChargeIdResponse> {
    return nomisApiService.getAdjudicationIds(
      fromDate = migrationFilter.fromDate,
      toDate = migrationFilter.toDate,
      pageNumber = pageNumber,
      pageSize = pageSize,
      prisonIds = migrationFilter.prisonIds,
    )
  }

  override suspend fun migrateNomisEntity(context: MigrationContext<AdjudicationChargeIdResponse>) {
    log.info("attempting to migrate ${context.body}")
    val adjudicationNumber = context.body.adjudicationNumber
    val chargeSequence = context.body.chargeSequence
    val offenderNo = context.body.offenderNo

    adjudicationsMappingService.findNomisMapping(
      adjudicationNumber = adjudicationNumber,
      chargeSequence = chargeSequence,
    )
      ?.run {
        log.info("Will not migrate the adjudication since it is migrated already, NOMIS adjudicationNumber is $adjudicationNumber/$chargeSequence as part of migration ${this.label ?: "NONE"} (${this.mappingType})")
      }
      ?: run {
        val nomisAdjudication =
          nomisApiService.getAdjudicationCharge(
            adjudicationNumber = adjudicationNumber,
            chargeSequence = chargeSequence,
          )

        val dpsAdjudication = adjudicationsService.createAdjudication(nomisAdjudication.toAdjudication())
        val chargeNumber = dpsAdjudication.chargeNumberMapping.chargeNumber
        createAdjudicationMapping(
          adjudicationNumber = adjudicationNumber,
          chargeSequence = chargeSequence,
          chargeNumber = chargeNumber,
          context = context,
        )

        telemetryClient.trackEvent(
          "adjudications-migration-entity-migrated",
          mapOf(
            "adjudicationNumber" to adjudicationNumber.toString(),
            "chargeSequence" to chargeSequence.toString(),
            "chargeNumber" to chargeNumber,
            "offenderNo" to offenderNo,
            "migrationId" to context.migrationId,
          ),
          null,
        )
      }
  }

  private suspend fun createAdjudicationMapping(
    adjudicationNumber: Long,
    chargeSequence: Int,
    chargeNumber: String,
    context: MigrationContext<*>,
  ) = try {
    adjudicationsMappingService.createMapping(
      AdjudicationMapping(
        adjudicationNumber = adjudicationNumber,
        chargeSequence = chargeSequence,
        chargeNumber = chargeNumber,
        label = context.migrationId,
        mappingType = "MIGRATED",
      ),
    )
  } catch (e: Exception) {
    log.error(
      "Failed to create mapping for adjudicationNumber: $adjudicationNumber/$chargeSequence",
      e,
    )
    queueService.sendMessage(
      MigrationMessageType.RETRY_MIGRATION_MAPPING,
      MigrationContext(
        context = context,
        body = AdjudicationMapping(
          adjudicationNumber = adjudicationNumber,
          chargeSequence = chargeSequence,
          chargeNumber = chargeNumber,
          mappingType = "MIGRATED",
        ),
      ),
    )
  }
}
