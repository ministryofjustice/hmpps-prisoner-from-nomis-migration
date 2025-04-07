package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBodilessEntity
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.CourtCaseLegacyData
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.LegacyChargeCreatedResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.LegacyCourtAppearanceCreatedResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.LegacyCourtCaseCreatedResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.LegacyCreateCharge
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.LegacyCreateCourtAppearance
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.LegacyCreateCourtCase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.LegacyCreateSentence
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.LegacySentenceCreatedResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.LegacyUpdateCharge
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.LegacyUpdateWholeCharge
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.MigrationCreateCourtCases
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.MigrationCreateCourtCasesResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodilessEntityIgnoreNotFound

@Service
class CourtSentencingDpsApiService(@Qualifier("courtSentencingApiWebClient") private val webClient: WebClient) {
  suspend fun createCourtCase(courtCase: LegacyCreateCourtCase): LegacyCourtCaseCreatedResponse = webClient
    .post()
    .uri("/legacy/court-case")
    .bodyValue(courtCase)
    .retrieve()
    .awaitBody()

  suspend fun createCourtCaseMigration(courtCase: MigrationCreateCourtCases): MigrationCreateCourtCasesResponse = webClient
    .post()
    .uri("/legacy/court-case/migration")
    .bodyValue(courtCase)
    .retrieve()
    .awaitBody()

  suspend fun updateCourtCasePostMerge(
    courtCasesCreated: MigrationCreateCourtCases,
    courtCasesDeactivated: List<Pair<String, LegacyCreateCourtCase>>,
    sentencesDeactivated: List<Pair<String, LegacyCreateSentence>>,
  ): MigrationCreateCourtCasesResponse {
    // TODO - switch to a DPS API that:
    //  - merges the cases
    //  - created new cases
    //  - updates sentences
    courtCasesDeactivated.forEach { (courtCaseId, courtCase) -> updateCourtCase(courtCaseId, courtCase) }
    sentencesDeactivated.forEach { (sentenceId, sentence) -> updateSentence(sentenceId, sentence) }
    return createCourtCaseMigration(courtCasesCreated)
  }

  suspend fun updateCourtCase(courtCaseId: String, courtCase: LegacyCreateCourtCase): ResponseEntity<Void> = webClient
    .put()
    .uri("/legacy/court-case/{courtCaseId}", courtCaseId)
    .bodyValue(courtCase)
    .retrieve()
    .awaitBodilessEntity()

  suspend fun deleteCourtCase(courtCaseId: String) = webClient
    .delete()
    .uri("/legacy/court-case/{courtCaseId}", courtCaseId)
    .retrieve()
    .awaitBodilessEntityIgnoreNotFound()

  suspend fun createCourtAppearance(courtAppearance: LegacyCreateCourtAppearance): LegacyCourtAppearanceCreatedResponse = webClient
    .post()
    .uri("/legacy/court-appearance")
    .bodyValue(courtAppearance)
    .retrieve()
    .awaitBody()

  suspend fun updateCourtAppearance(courtAppearanceId: String, courtAppearance: LegacyCreateCourtAppearance) = webClient
    .put()
    .uri("/legacy/court-appearance/{courtAppearanceId}", courtAppearanceId)
    .bodyValue(courtAppearance)
    .retrieve()
    .awaitBodilessEntity()

  suspend fun deleteCourtAppearance(courtAppearanceId: String) = webClient
    .delete()
    .uri("/legacy/court-appearance/{courtAppearanceId}", courtAppearanceId)
    .retrieve()
    .awaitBodilessEntityIgnoreNotFound()

  suspend fun associateExistingCourtCharge(courtAppearanceId: String, chargeId: String, charge: LegacyUpdateCharge) = webClient
    .put()
    .uri("/legacy/court-appearance/{courtAppearanceId}/charge/{chargeId}", courtAppearanceId, chargeId)
    .bodyValue(charge)
    .retrieve()
    .awaitBodilessEntity()

  // add a court charge and associate it with the given appearance (will create sync mapping)
  suspend fun addNewCourtCharge(charge: LegacyCreateCharge): LegacyChargeCreatedResponse = webClient
    .post()
    .uri("/legacy/charge")
    .bodyValue(charge)
    .retrieve()
    .awaitBody()

  // remove association between court appearance and charge
  suspend fun removeCourtChargeAssociation(courtAppearanceId: String, chargeId: String) = webClient
    .delete()
    .uri("/legacy/court-appearance/{courtAppearanceId}/charge/{chargeId}", courtAppearanceId, chargeId)
    .retrieve()
    .awaitBodilessEntity()

  suspend fun updateCourtCharge(chargeId: String, appearanceId: String, charge: LegacyUpdateCharge) = webClient
    .put()
    .uri("/legacy/charge/{chargeId}/appearance/{appearanceId}", chargeId, appearanceId)
    .bodyValue(charge)
    .retrieve()
    .awaitBodilessEntity()

  suspend fun updateChargeOffence(chargeId: String, charge: LegacyUpdateWholeCharge) = webClient
    .put()
    .uri("/legacy/charge/{chargeId}", chargeId)
    .bodyValue(charge)
    .retrieve()
    .awaitBodilessEntity()

  suspend fun createSentence(sentence: LegacyCreateSentence): LegacySentenceCreatedResponse = webClient
    .post()
    .uri("/legacy/sentence")
    .bodyValue(sentence)
    .retrieve()
    .awaitBody()

  suspend fun deleteSentence(sentenceId: String) = webClient
    .delete()
    .uri("/legacy/sentence/{sentenceId}", sentenceId)
    .retrieve()
    .awaitBodilessEntityIgnoreNotFound()

  suspend fun updateSentence(sentenceId: String, sentence: LegacyCreateSentence): ResponseEntity<Void> = webClient
    .put()
    .uri("/legacy/sentence/{sentenceId}", sentenceId)
    .bodyValue(sentence)
    .retrieve()
    .awaitBodilessEntity()

  suspend fun refreshCaseIdentifiers(courtCaseId: String, courtCaseLegacyData: CourtCaseLegacyData) {
    webClient
      .put()
      .uri("/court-case/{courtCaseId}/case-references/refresh", courtCaseId)
      .bodyValue(courtCaseLegacyData)
      .retrieve()
      .awaitBodilessEntity()
  }
}
