package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBodilessEntity
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.BookingCreateCourtCases
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.BookingCreateCourtCasesResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.LegacyChargeCreatedResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.LegacyCourtAppearanceCreatedResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.LegacyCourtCaseCreatedResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.LegacyCreateCharge
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.LegacyCreateCourtAppearance
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.LegacyCreateCourtCase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.LegacyCreatePeriodLength
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.LegacyCreateSentence
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.LegacyLinkChargeToCase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.LegacyPeriodLengthCreatedResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.LegacySentenceCreatedResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.LegacyUpdateCharge
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.LegacyUpdateWholeCharge
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.MergeCreateCourtCasesResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.MergePerson
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.MigrationCreateCourtCases
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.MigrationCreateCourtCasesResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.RefreshCaseReferences
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodilessEntityIgnoreNotFound

@Service
class CourtSentencingDpsApiService(
  @Qualifier("courtSentencingApiWebClient") private val webClient: WebClient,
  @Qualifier("courtSentencingApiTimeCriticalWebClient") private val timeCriticalWebClient: WebClient,
) {
  suspend fun createCourtCase(courtCase: LegacyCreateCourtCase): LegacyCourtCaseCreatedResponse = webClient
    .post()
    .uri("/legacy/court-case")
    .bodyValue(courtCase)
    .retrieve()
    .awaitBody()

  suspend fun createCourtCaseMigration(courtCase: MigrationCreateCourtCases, deleteExisting: Boolean): MigrationCreateCourtCasesResponse = webClient
    .post()
    .uri {
      it.path("/legacy/court-case/migration")
        .queryParam("deleteExisting", "{deleteExisting}")
        .build(deleteExisting)
    }
    .bodyValue(courtCase)
    .retrieve()
    .awaitBody()

  suspend fun createCourtCaseCloneBooking(courtCase: BookingCreateCourtCases): BookingCreateCourtCasesResponse = webClient
    .post()
    .uri("/legacy/court-case/booking")
    .bodyValue(courtCase)
    .retrieve()
    .awaitBody()

  suspend fun createCourtCaseMerge(mergePerson: MergePerson, offenderNo: String): MergeCreateCourtCasesResponse = webClient
    .post()
    .uri("/legacy/court-case/merge/person/{offenderNo}", offenderNo)
    .bodyValue(mergePerson)
    .retrieve()
    .awaitBody()

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

  suspend fun deleteSentence(sentenceId: String) = timeCriticalWebClient
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

  suspend fun createPeriodLength(sentence: LegacyCreatePeriodLength): LegacyPeriodLengthCreatedResponse = webClient
    .post()
    .uri("/legacy/period-length")
    .bodyValue(sentence)
    .retrieve()
    .awaitBody()

  suspend fun deletePeriodLength(periodLengthId: String) = timeCriticalWebClient
    .delete()
    .uri("/legacy/period-length/{periodLengthId}", periodLengthId)
    .retrieve()
    .awaitBodilessEntityIgnoreNotFound()

  suspend fun updatePeriodLength(periodLengthId: String, period: LegacyCreatePeriodLength): ResponseEntity<Void> = webClient
    .put()
    .uri("/legacy/period-length/{periodLengthId}", periodLengthId)
    .bodyValue(period)
    .retrieve()
    .awaitBodilessEntity()

  suspend fun refreshCaseIdentifiers(courtCaseId: String, courtCaseLegacyData: RefreshCaseReferences) {
    webClient
      .put()
      .uri("/legacy/court-case/{courtCaseId}/case-references/refresh", courtCaseId)
      .bodyValue(courtCaseLegacyData)
      .retrieve()
      .awaitBodilessEntity()
  }
  suspend fun linkCase(sourceCourtCaseId: String, targetCourtCaseId: String) {
    webClient
      .put()
      .uri("/legacy/court-case/{sourceCourtCaseUuid}/link/{targetCourtCaseUuid}", sourceCourtCaseId, targetCourtCaseId)
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun unlinkCase(sourceCourtCaseId: String, targetCourtCaseId: String) {
    webClient
      .put()
      .uri("/legacy/court-case/{sourceCourtCaseUuid}/unlink/{targetCourtCaseUuid}", sourceCourtCaseId, targetCourtCaseId)
      .retrieve()
      .awaitBodilessEntity()
  }

  @Suppress("unused")
  suspend fun linkChargeToCase(courtAppearanceId: String, chargeId: String, linkData: LegacyLinkChargeToCase) {
    webClient
      .put()
      .uri("/legacy/court-appearance/{courtAppearanceUuid}/charge/{chargeUuid}/link", courtAppearanceId, chargeId)
      .bodyValue(linkData)
      .retrieve()
      .awaitBodilessEntity()
  }
}
