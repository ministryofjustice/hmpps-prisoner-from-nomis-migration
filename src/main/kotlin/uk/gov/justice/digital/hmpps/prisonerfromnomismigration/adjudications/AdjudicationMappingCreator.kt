package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model.ChargeNumberMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model.HearingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model.HearingMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model.MigrateResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model.ReportedAdjudicationResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.AdjudicationChargeResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.Hearing
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
      punishmentMappings = listOf(),
    )
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
}
