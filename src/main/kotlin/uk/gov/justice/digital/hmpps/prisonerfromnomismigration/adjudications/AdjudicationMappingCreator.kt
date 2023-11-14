package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model.ChargeNumberMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model.MigrateResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model.ReportedAdjudicationResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.AdjudicationChargeResponse

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
      hearingMappings = listOf(),
      punishmentMappings = listOf(),
    )
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
