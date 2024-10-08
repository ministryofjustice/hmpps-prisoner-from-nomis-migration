package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageImpl
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model.AdjudicationMigrateDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model.DisIssued
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model.MigrateDamage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model.MigrateDamage.DamageType.CLEANING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model.MigrateDamage.DamageType.ELECTRICAL_REPAIR
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model.MigrateDamage.DamageType.FURNITURE_OR_FABRIC_REPAIR
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model.MigrateDamage.DamageType.LOCK_REPAIR
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model.MigrateDamage.DamageType.PLUMBING_REPAIR
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model.MigrateDamage.DamageType.REDECORATION
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model.MigrateEvidence
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model.MigrateEvidence.EvidenceCode.BAGGED_AND_TAGGED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model.MigrateEvidence.EvidenceCode.OTHER
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model.MigrateEvidence.EvidenceCode.PHOTO
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model.MigrateHearing
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model.MigrateHearing.OicHearingType.GOV
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model.MigrateHearing.OicHearingType.GOV_ADULT
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model.MigrateHearing.OicHearingType.GOV_YOI
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model.MigrateHearing.OicHearingType.INAD_ADULT
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model.MigrateHearing.OicHearingType.INAD_YOI
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model.MigrateHearingResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model.MigrateOffence
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model.MigratePrisoner
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model.MigratePunishment
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model.MigrateResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model.MigrateWitness
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model.MigrateWitness.WitnessType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model.MigrateWitness.WitnessType.OTHER_PERSON
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model.MigrateWitness.WitnessType.PRISONER
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model.MigrateWitness.WitnessType.STAFF
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model.MigrateWitness.WitnessType.VICTIM
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model.ReportingOfficer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.AdjudicationAllMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.AdjudicationAllMappingDto.MappingType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.AdjudicationHearingMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.AdjudicationMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.AdjudicationPunishmentMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.AdjudicationCharge
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.AdjudicationChargeIdResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.AdjudicationChargeResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.AdjudicationIncident
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.Evidence
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.Hearing
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.HearingNotification
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.HearingResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.HearingResultAward
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.Repair
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.Staff
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

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
    witnesses = this.incident.staffWitnesses.map { it.toWitness(STAFF) } +
      this.incident.prisonerWitnesses.map { it.toWitness(OTHER_PERSON) } +
      this.incident.staffVictims.map { it.toWitness(VICTIM) } +
      this.incident.prisonerVictims.map { it.toWitness(VICTIM) } +
      this.incident.otherPrisonersInvolved.map { it.toWitness(PRISONER) } +
      this.incident.reportingOfficers.map { it.toWitness(OTHER_PERSON) } +
      this.incident.otherStaffInvolved.map { it.toWitness(OTHER_PERSON) },
    damages = this.incident.repairs.map { it.toDamage() },
    evidence = this.investigations.flatMap { investigation -> investigation.evidence.map { it.toEvidence() } } + this.charge.toEvidence(
      incident,
    ),
    punishments = this.hearings.flatMap { it.toHearingResultAwards() },
    hearings = this.hearings.map { it.toHearing() },
    disIssued = this.hearings.flatMap { hearing -> hearing.notifications.map { it.toIssued() } },
    nomisSplitRecord = this.hasMultipleCharges,
  )

private fun AdjudicationCharge.toEvidence(incident: AdjudicationIncident): List<MigrateEvidence> =
  this.takeUnless { it.evidence.isNullOrEmpty() && it.reportDetail.isNullOrEmpty() }.let { charge ->
    return charge?.let {
      listOf(
        MigrateEvidence(
          evidenceCode = OTHER,
          details = listOfNotNull(
            charge.evidence.takeUnless { it.isNullOrEmpty() },
            charge.reportDetail.takeUnless { it.isNullOrEmpty() },
          ).joinToString(separator = " - "),
          reporter = incident.reportingStaff.username,
          dateAdded = incident.reportedDate,
        ),
      )
    } ?: emptyList()
  }

private fun Staff.toWitness(type: WitnessType) = MigrateWitness(
  firstName = this.firstName,
  lastName = this.lastName,
  // never null for a witness
  createdBy = this.createdByUsername!!,
  type,
  dateAdded = this.dateAddedToIncident ?: LocalDate.now(),
  comment = this.comment,
)

private fun Prisoner.toWitness(type: WitnessType) = MigrateWitness(
  firstName = this.firstName ?: "",
  lastName = this.lastName,
  createdBy = this.createdByUsername,
  type,
  dateAdded = this.dateAddedToIncident,
  comment = this.comment,
)

private fun HearingNotification.toIssued() = DisIssued(
  issuingOfficer = this.notifiedStaff.username,
  dateTimeOfIssue = this.deliveryDate.atTime(LocalTime.parse(this.deliveryTime))
    .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
)

private fun Hearing.toHearingResultAwards(): List<MigratePunishment> =
  this.hearingResults
    .flatMap { hearingResult ->
      hearingResult.resultAwards.map {
        MigratePunishment(
          sanctionCode = it.sanctionType?.code
            ?: throw IllegalArgumentException("Result award must have a sanctionType"),
          sanctionStatus = it.sanctionStatus?.code
            ?: throw IllegalArgumentException("Result award must have a sanctionStatus"),
          effectiveDate = it.effectiveDate,
          statusDate = it.statusDate,
          sanctionSeq = it.sequence.toLong(),
          comment = it.comment,
          compensationAmount = it.compensationAmount,
          days = it.asDays(),
          consecutiveChargeNumber = it.consecutiveAward.toConsecutiveChargeNumber(),
          // if award was created by NOMIS merge user than use person who create result
          createdBy = it.createdByUsername.takeUnless { username -> username == "SYS" }
            ?: hearingResult.createdByUsername,
          createdDateTime = it.createdDateTime,
        )
      }
    }

fun HearingResultAward.asDays() = this.sanctionDays + this.sanctionMonths.asDays(this.effectiveDate)
private fun HearingResultAward?.toConsecutiveChargeNumber(): String? =
  this?.let { "$adjudicationNumber-$chargeSequence" }

private operator fun Int?.plus(second: Int?): Int? = when {
  this == null && second == null -> null
  this == null -> second
  second == null -> this
  else -> this + second
}

private fun Int?.asDays(effectiveDate: LocalDate): Int? =
  this?.let { ChronoUnit.DAYS.between(effectiveDate, effectiveDate.plusMonths(this.toLong())).toInt() }

private fun Hearing.toHearing() = MigrateHearing(
  oicHearingId = this.hearingId,
  oicHearingType = when (this.type?.code) {
    "GOV" -> GOV
    "GOV_ADULT" -> GOV_ADULT
    "GOV_YOI" -> GOV_YOI
    "INAD_ADULT" -> INAD_ADULT
    "INAD_YOI" -> INAD_YOI
    else -> GOV // TODO - we can't do NULL right now until DPS API is changed
  },
  hearingDateTime = this.hearingDate?.atTime(LocalTime.parse(this.hearingTime))?.format(
    DateTimeFormatter.ISO_LOCAL_DATE_TIME,
  ) ?: throw IllegalArgumentException("Hearing must have a date time"),
  locationId = this.internalLocation?.locationId ?: throw IllegalArgumentException("Hearing must have a location"),
  adjudicator = this.hearingStaff?.username,
  commentText = this.comment,
  hearingResult = this.hearingResults.toHearingResult(),
  representative = this.representativeText,
)

private fun List<HearingResult>.toHearingResult(): MigrateHearingResult? =
  this.firstOrNull()?.let {
    MigrateHearingResult(
      // Never null in NOMIS though schema allows it
      plea = it.pleaFindingType?.code ?: throw IllegalArgumentException("Hearing result must have a plea"),
      // Never null in NOMIS though schema allows it
      finding = it.findingType?.code ?: throw IllegalArgumentException("Hearing result must have a finding"),
      createdDateTime = it.createdDateTime,
      createdBy = it.createdByUsername,
    )
  }

private fun Evidence.toEvidence() = MigrateEvidence(
  evidenceCode = when (this.type.code) {
    "PHOTO" -> PHOTO
    "EVI_BAG" -> BAGGED_AND_TAGGED
    else -> OTHER
  },
  details = this.detail,
  reporter = this.createdByUsername,
  dateAdded = this.date,
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
  repairCost = this.cost,
)

@Service
class AdjudicationsMigrationService(
  private val nomisApiService: NomisApiService,
  private val adjudicationsMappingService: AdjudicationsMappingService,
  private val adjudicationsService: AdjudicationsService,
  private val mappingCreator: AdjudicationMappingCreator,
  @Value("\${adjudications.page.size:1000}") pageSize: Long,
  @Value("\${complete-check.delay-seconds}") completeCheckDelaySeconds: Int,
  @Value("\${complete-check.count}") completeCheckCount: Int,
  @Value("\${feature.adjudications.report.mode:false}")
  private val reportMode: Boolean,
) : MigrationService<AdjudicationsMigrationFilter, AdjudicationChargeIdResponse, AdjudicationAllMappingDto>(
  mappingService = adjudicationsMappingService,
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
  ): PageImpl<AdjudicationChargeIdResponse> = nomisApiService.getAdjudicationIds(
    fromDate = migrationFilter.fromDate,
    toDate = migrationFilter.toDate,
    pageNumber = pageNumber,
    pageSize = pageSize,
    prisonIds = migrationFilter.prisonIds,
  )

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

        try {
          val mapping = adjudicationsService.createAdjudication(nomisAdjudication.toAdjudication())
            ?: mappingCreator.createMigrationMappingResponse(nomisAdjudication)

          mapping?.run {
            createAdjudicationMapping(mapping, context = context)

            telemetryClient.trackEvent(
              "adjudications-migration-entity-migrated",
              mapOf(
                "adjudicationNumber" to adjudicationNumber.toString(),
                "chargeSequence" to chargeSequence.toString(),
                "prisonId" to nomisAdjudication.incident.prison.code,
                "chargeNumber" to mapping.chargeNumberMapping.chargeNumber,
                "offenderNo" to offenderNo,
                "migrationId" to context.migrationId,
              ),
              null,
            )
          } ?: run {
            // unlikely to get here since we checked the mapping just above
            telemetryClient.trackEvent(
              "adjudications-migration-entity-already-migrated",
              mapOf(
                "adjudicationNumber" to adjudicationNumber.toString(),
                "chargeSequence" to chargeSequence.toString(),
                "prisonId" to nomisAdjudication.incident.prison.code,
                "offenderNo" to offenderNo,
                "migrationId" to context.migrationId,
              ),
              null,
            )
          }
        } catch (e: WebClientResponseException) { // only required while DPS is analysing the data failures
          if (reportMode && e.statusCode.is4xxClientError) {
            log.error("Ignoring error ${e.statusCode} for adjudicationNumber: $adjudicationNumber/$chargeSequence")
            telemetryClient.trackEvent(
              "adjudications-migration-entity-failed",
              mapOf(
                "adjudicationNumber" to adjudicationNumber.toString(),
                "chargeSequence" to chargeSequence.toString(),
                "prisonId" to nomisAdjudication.incident.prison.code,
                "resultCode" to e.statusCode.toString(),
                "offenderNo" to offenderNo,
                "migrationId" to context.migrationId,
              ),
              null,
            )
          } else {
            throw e
          }
        }
      }
  }

  private suspend fun createAdjudicationMapping(
    mapping: MigrateResponse,
    context: MigrationContext<*>,
  ) = AdjudicationAllMappingDto(
    adjudicationId = AdjudicationMappingDto(
      adjudicationNumber = mapping.chargeNumberMapping.oicIncidentId,
      chargeSequence = mapping.chargeNumberMapping.offenceSequence.toInt(),
      chargeNumber = mapping.chargeNumberMapping.chargeNumber,
    ),
    hearings = mapping.hearingMappings?.map {
      AdjudicationHearingMappingDto(
        dpsHearingId = it.hearingId.toString(),
        nomisHearingId = it.oicHearingId,
      )
    } ?: emptyList(),
    punishments = mapping.punishmentMappings?.filter {
      (it.sanctionSeq != null).also { hasSanction ->
        if (!hasSanction) {
          log.warn("Ignoring mapping for punishment ${it.punishmentId} for booking ${it.bookingId}. DPS has no sanctionSeq as this is a WIP")
        }
      }
    }?.map {
      AdjudicationPunishmentMappingDto(
        dpsPunishmentId = it.punishmentId.toString(),
        nomisBookingId = it.bookingId,
        nomisSanctionSequence = it.sanctionSeq!!.toInt(),
      )
    } ?: emptyList(),
    label = context.migrationId,
    mappingType = MappingType.MIGRATED,
  ).run {
    try {
      adjudicationsMappingService.createMapping(this)
    } catch (e: Exception) {
      log.error(
        "Failed to create mapping for adjudicationNumber: ${mapping.chargeNumberMapping.oicIncidentId}/${mapping.chargeNumberMapping.offenceSequence}",
        e,
      )
      queueService.sendMessage(
        MigrationMessageType.RETRY_MIGRATION_MAPPING,
        MigrationContext(
          context = context,
          body = this,
        ),
      )
    }
  }
}
