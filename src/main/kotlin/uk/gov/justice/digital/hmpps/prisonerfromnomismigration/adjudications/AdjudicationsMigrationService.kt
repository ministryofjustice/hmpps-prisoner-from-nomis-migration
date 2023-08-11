package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageImpl
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model.AdjudicationMigrateDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model.MigrateDamage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model.MigrateDamage.DamageType.CLEANING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model.MigrateDamage.DamageType.ELECTRICAL_REPAIR
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model.MigrateDamage.DamageType.FURNITURE_OR_FABRIC_REPAIR
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model.MigrateDamage.DamageType.LOCK_REPAIR
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model.MigrateDamage.DamageType.PLUMBING_REPAIR
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model.MigrateDamage.DamageType.REDECORATION
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model.MigrateEvidence
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model.MigrateEvidence.EvidenceCode.BAGGED_AND_TAGGED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model.MigrateEvidence.EvidenceCode.CCTV
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model.MigrateEvidence.EvidenceCode.PHOTO
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model.MigrateOffence
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model.MigratePrisoner
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model.MigrateWitness
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model.MigrateWitness.WitnessType.OTHER_PERSON
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model.MigrateWitness.WitnessType.PRISONER
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model.MigrateWitness.WitnessType.STAFF
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model.MigrateWitness.WitnessType.VICTIM
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model.ReportingOfficer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.AdjudicationChargeIdResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.AdjudicationChargeResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.AdjudicationResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.Evidence
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.Repair
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.AuditService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationHistoryService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService
import java.time.LocalTime
import java.time.format.DateTimeFormatter

fun AdjudicationChargeResponse.toAdjudication(): AdjudicationMigrateDto =
  AdjudicationMigrateDto(
    agencyIncidentId = this.incident.adjudicationIncidentId,
    oicIncidentId = this.adjudicationNumber,
    offenceSequence = this.charge.chargeSequence.toLong(),
    bookingId = this.bookingId,
    agencyId = this.incident.prison.code,
    incidentDateTime = this.incident.incidentDate.atTime(LocalTime.parse(this.incident.incidentTime)).format(
      DateTimeFormatter.ISO_LOCAL_DATE_TIME,
    ),
    reportedDateTime = this.incident.reportedDate.atTime(LocalTime.parse(this.incident.reportedTime)).format(
      DateTimeFormatter.ISO_LOCAL_DATE_TIME,
    ),
    locationId = this.incident.internalLocation.locationId,
    statement = incident.details ?: "",
    reportingOfficer = ReportingOfficer(this.incident.reportingStaff.username),
    createdByUsername = this.incident.createdByUsername,
    prisoner = MigratePrisoner(
      prisonerNumber = this.offenderNo,
      gender = this.gender.code,
      currentAgencyId = this.currentPrison?.code,
    ),
    offence = MigrateOffence(
      offenceCode = this.charge.offence.code,
      offenceDescription = this.charge.offence.description,
    ),
    witnesses = this.incident.staffWitnesses.map {
      MigrateWitness(
        firstName = it.firstName,
        lastName = it.lastName,
        createdBy = this.incident.createdByUsername,
        STAFF,
      )
    } + this.incident.prisonerWitnesses.map {
      MigrateWitness(
        firstName = it.firstName ?: "",
        lastName = it.lastName,
        createdBy = this.incident.createdByUsername,
        OTHER_PERSON,
      )
    } + this.incident.staffVictims.map {
      MigrateWitness(
        firstName = it.firstName,
        lastName = it.lastName,
        createdBy = this.incident.createdByUsername,
        VICTIM,
      )
    } + this.incident.prisonerVictims.map {
      MigrateWitness(
        firstName = it.firstName ?: "",
        lastName = it.lastName,
        createdBy = this.incident.createdByUsername,
        VICTIM,
      )
    } + this.incident.otherPrisonersInvolved.map {
      MigrateWitness(
        firstName = it.firstName ?: "",
        lastName = it.lastName,
        createdBy = this.incident.createdByUsername,
        PRISONER,
      )
    } + this.incident.reportingOfficers.map {
      MigrateWitness(
        firstName = it.firstName,
        lastName = it.lastName,
        createdBy = this.incident.createdByUsername,
        OTHER_PERSON,
      )
    } + this.incident.otherStaffInvolved.map {
      MigrateWitness(
        firstName = it.firstName,
        lastName = it.lastName,
        createdBy = this.incident.createdByUsername,
        OTHER_PERSON,
      )
    },
    damages = this.incident.repairs.map { it.toDamage() },
    evidence = this.investigations.flatMap { investigation -> investigation.evidence.map { it.toEvidence() } },
    punishments = emptyList(),
    hearings = emptyList(),
  )

private fun Evidence.toEvidence() = MigrateEvidence(
  evidenceCode = when (this.type.code) {
    "PHOTO" -> PHOTO
    "EVI_BAG" -> BAGGED_AND_TAGGED
    else -> CCTV // TODO - how to map all the codes?
  },
  details = this.detail,
  reporter = this.createdByUsername,
)

private fun Repair.toDamage() = MigrateDamage(
  damageType = when (this.type.code) {
    "ELEC" -> ELECTRICAL_REPAIR
    "PLUM" -> PLUMBING_REPAIR
    "DECO" -> REDECORATION
    "FABR" -> FURNITURE_OR_FABRIC_REPAIR
    "CLEA" -> CLEANING
    "LOCK" -> LOCK_REPAIR
    else -> throw IllegalArgumentException("Unknown repair type ${this.type.code}")
  },
  details = this.comment,
  createdBy = this.createdByUsername,
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
