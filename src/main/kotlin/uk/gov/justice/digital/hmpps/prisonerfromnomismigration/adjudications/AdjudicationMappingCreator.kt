package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model.ChargeNumberMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model.HearingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model.HearingMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model.MigrateResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model.PunishmentDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model.PunishmentDto.Type
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model.PunishmentMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model.ReportedAdjudicationResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.AdjudicationChargeResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.Hearing
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.HearingResultAward
import java.time.LocalDateTime
import java.time.LocalTime

@Component
class AdjudicationMappingCreator(
  private val adjudicationsService: AdjudicationsService,
  private val mappingService: AdjudicationsMappingService,
) {
  suspend fun createMigrationMappingResponse(nomisAdjudication: AdjudicationChargeResponse): MigrateResponse? {
    if (hasAlreadyCreatedMappings(nomisAdjudication.adjudicationNumber, nomisAdjudication.adjudicationSequence)) {
      return null
    }
    val dpsAdjudication = getDpsAdjudication(
      nomisAdjudication.adjudicationNumber,
      nomisAdjudication.adjudicationSequence,
      nomisAdjudication.incident.prison.code,
    )
    return MigrateResponse(
      chargeNumberMapping = ChargeNumberMapping(
        chargeNumber = dpsAdjudication.reportedAdjudication.chargeNumber,
        oicIncidentId = nomisAdjudication.adjudicationNumber,
        offenceSequence = nomisAdjudication.adjudicationSequence.toLong(),
      ),
      hearingMappings = hearingMappings(nomisAdjudication.hearings, dpsAdjudication.reportedAdjudication.hearings),
      punishmentMappings = punishmentMappings(
        nomisAdjudication.bookingId,
        nomisAdjudication.hearings
          .flatMap { it.hearingResults }
          .flatMap { it.resultAwards },
        dpsAdjudication.reportedAdjudication.punishments,
      ),
    )
  }

  private fun punishmentMappings(
    nomisBookingId: Long,
    nomisPunishments: List<HearingResultAward>,
    dpsPunishments: List<PunishmentDto>,
  ): List<PunishmentMapping> {
    // this is "good" enough to ensure the small number of records map
    // note: they don't need to be perfect since if they are ever updated in
    // DPS they will be re-synced to NOMIS as a batch
    val nomisPunishmentsSorted = nomisPunishments
      .filter { it.sanctionType != null && it.sanctionStatus != null }
      .sortedWith(compareBy<HearingResultAward> { it.toDpsPunishmentType() }.thenBy { it.asDays() })
    val dpsPunishmentsSorted = dpsPunishments
      .sortedWith(compareBy<PunishmentDto> { it.type }.thenBy { it.schedule.days })

    if (nomisPunishmentsSorted.size != dpsPunishmentsSorted.size) {
      throw IllegalStateException("Unable to find matching punishments for adjudication, NOMIS has ${nomisPunishmentsSorted.size} valid awards, DPS has ${dpsPunishmentsSorted.size} punishments")
    }

    return nomisPunishmentsSorted.zip(dpsPunishmentsSorted)
      .map {
        PunishmentMapping(
          sanctionSeq = it.first.sequence.toLong(),
          bookingId = nomisBookingId,
          punishmentId = it.second.id!!,
        )
      }
  }

  private fun hearingMappings(nomisHearings: List<Hearing>, dpsHearings: List<HearingDto>): List<HearingMapping> {
    val nomisHearingsSorted = nomisHearings
      .filter { it.hearingDate != null && it.hearingTime != null }
      .sortedBy { it.hearingDate!!.atTime(LocalTime.parse(it.hearingTime)) }
    val dpsHearingsSorted = dpsHearings
      .sortedBy { LocalDateTime.parse(it.dateTimeOfHearing) }

    if (nomisHearingsSorted.size != dpsHearingsSorted.size) {
      throw IllegalStateException("Unable to find matching hearings for adjudication, NOMIS has ${nomisHearingsSorted.size} valid hearings, DPS has ${dpsHearingsSorted.size} hearings")
    }

    return nomisHearingsSorted.zip(dpsHearingsSorted)
      .map { HearingMapping(oicHearingId = it.first.hearingId, hearingId = it.second.id!!) }
  }

  suspend fun hasAlreadyCreatedMappings(adjudicationNumber: Long, chargeSequence: Int): Boolean =
    mappingService.findNomisMapping(adjudicationNumber, chargeSequence)?.let { true } ?: false

  suspend fun getDpsAdjudication(
    nomisAdjudicationNumber: Long,
    chargeSequence: Int,
    prisonId: String,
  ): ReportedAdjudicationResponse {
    val dpsChargeNumber = "$nomisAdjudicationNumber-$chargeSequence"
    return adjudicationsService.getCharge(dpsChargeNumber, prisonId)
      ?: (
        adjudicationsService.getCharge("$nomisAdjudicationNumber", prisonId)
          ?: throw IllegalStateException("Unable to find adjudication $nomisAdjudicationNumber with charge sequence $chargeSequence")
        )
  }

  fun HearingResultAward.toDpsPunishmentType(): Type = when (this.sanctionType?.code) {
    // exact copy of DPS mapping code
    OicSanctionCode.ADA.name -> if (prospectiveStatuses.contains(this.sanctionStatus?.code)) Type.PROSPECTIVE_DAYS else Type.ADDITIONAL_DAYS
    OicSanctionCode.PADA.name -> Type.PROSPECTIVE_DAYS
    OicSanctionCode.EXTRA_WORK.name -> Type.EXCLUSION_WORK
    OicSanctionCode.EXTW.name -> Type.EXTRA_WORK
    OicSanctionCode.CAUTION.name -> Type.CAUTION
    OicSanctionCode.CC.name -> Type.CONFINEMENT
    OicSanctionCode.REMACT.name -> Type.REMOVAL_ACTIVITY
    OicSanctionCode.REMWIN.name -> Type.REMOVAL_WING
    OicSanctionCode.STOP_PCT.name -> Type.EARNINGS
    OicSanctionCode.OTHER.name -> if (this.compensationAmount != null) Type.DAMAGES_OWED else Type.PRIVILEGE
    else -> Type.PRIVILEGE
  }
}

private enum class OicSanctionCode {
  ADA, CAUTION, CC, EXTRA_WORK, EXTW, OTHER, REMACT, REMWIN, STOP_PCT, PADA
}

private val prospectiveStatuses: List<String> = listOf(Status.PROSPECTIVE.name, Status.SUSP_PROSP.name)

private enum class Status {
  PROSPECTIVE, SUSP_PROSP
}
