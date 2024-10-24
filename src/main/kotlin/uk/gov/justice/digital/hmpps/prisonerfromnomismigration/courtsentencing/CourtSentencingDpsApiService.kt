package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBodilessEntity
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.CreateCharge
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.CreateCourtAppearance
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.CreateCourtAppearanceResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.CreateCourtCase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.CreateCourtCaseResponse
import java.time.LocalDateTime

@Service
class CourtSentencingDpsApiService(@Qualifier("courtSentencingApiWebClient") private val webClient: WebClient) {
  suspend fun createCourtCase(courtCase: CreateCourtCase): CreateCourtCaseResponse =
    webClient
      .post()
      .uri("/court-case")
      .bodyValue(courtCase)
      .retrieve()
      .awaitBody()

  // separating this out for now - DPS may use the same endpoint
  suspend fun createCourtCaseMigration(courtCase: CreateCourtCase): CreateCourtCaseMigrationResponse =
    webClient
      .post()
      .uri("/court-case")
      .bodyValue(courtCase)
      .retrieve()
      .awaitBody()

  suspend fun updateCourtCase(courtCaseId: String, courtCase: CreateCourtCase): CreateCourtCaseResponse =
    webClient
      .put()
      .uri("/court-case/{courtCaseId}", courtCaseId)
      .bodyValue(courtCase)
      .retrieve()
      .awaitBody()

  suspend fun deleteCourtCase(courtCaseId: String) =
    webClient
      .delete()
      .uri("/court-case/{courtCaseId}", courtCaseId)
      .retrieve()
      .awaitBodilessEntity()

  suspend fun createCourtAppearance(courtAppearance: CreateCourtAppearance): CreateCourtAppearanceResponse =
    webClient
      .post()
      .uri("/court-appearance")
      .bodyValue(courtAppearance)
      .retrieve()
      .awaitBody()

  suspend fun updateCourtAppearance(courtAppearanceId: String, courtAppearance: CreateCourtAppearance): CreateCourtAppearanceResponse =
    webClient
      .put()
      .uri("/court-appearance/{courtAppearanceId}", courtAppearanceId)
      .bodyValue(courtAppearance)
      .retrieve()
      .awaitBody()

  suspend fun deleteCourtAppearance(courtAppearanceId: String) =
    webClient
      .delete()
      .uri("/court-appearance/{courtAppearanceId}", courtAppearanceId)
      .retrieve()
      .awaitBodilessEntity()

  // sensible to assume that this endpoint could also update the charge
  suspend fun associateExistingCourtCharge(courtAppearanceId: String, charge: CreateCharge) =
    webClient
      .put()
      .uri("/court-appearance/{courtAppearanceId}/charge/{chargeId}", courtAppearanceId, charge.chargeUuid.toString())
      .bodyValue(charge)
      .retrieve()
      .awaitBodilessEntity()

  // add a court charge and associate it with the given appearance (will create sync mapping)
  suspend fun addNewCourtCharge(courtAppearanceId: String, charge: CreateCharge): CreateNewChargeResponse =
    webClient
      .post()
      .uri("/court-appearance/{courtAppearanceId}/charge", courtAppearanceId)
      .bodyValue(charge)
      .retrieve()
      .awaitBody()

  // remove association between court appearance and charge TODO determine whether DPS will delete any orphaned charges with no need to delete an unused charge explicitly
  suspend fun removeCourtCharge(courtAppearanceId: String, chargeId: String) =
    webClient
      .delete()
      .uri("/court-appearance/{courtAppearanceId}/charge/{chargeId}", courtAppearanceId, chargeId)
      .retrieve()
      .awaitBodilessEntity()

  // TODO not currently implement in DPS
  suspend fun updateCourtCharge(chargeId: String, charge: CreateCharge) =
    webClient
      .put()
      .uri("/charge/{chargeId}", chargeId)
      .bodyValue(charge)
      .retrieve()
      .awaitBodilessEntity()

  // TODO not currently implemented in DPS
  suspend fun createSentence(sentence: CreateSentenceRequest): CreateSentenceResponse =
    webClient
      .post()
      .uri("/sentence")
      .bodyValue(sentence)
      .retrieve()
      .awaitBody()

  suspend fun deleteSentence(sentenceId: String) =
    webClient
      .delete()
      .uri("/sentence/{sentenceId}", sentenceId)
      .retrieve()
      .awaitBodilessEntity()

  suspend fun updateSentence(sentenceId: String, sentence: CreateSentenceRequest): CreateSentenceResponse =
    webClient
      .put()
      .uri("sentence/{sentenceId}", sentenceId)
      .bodyValue(sentence)
      .retrieve()
      .awaitBody()

  suspend fun refreshCaseIdentifiers(courtCaseId: String, caseReferences: List<CaseReference>) {
    webClient
      .post()
      .uri("/court-case/{courtCaseId}/case-references/refresh", courtCaseId)
      .bodyValue(caseReferences)
      .retrieve()
      .awaitBodilessEntity()
  }
}

data class CreateNewChargeResponse(

  @field:JsonProperty("chargeUuid")
  val chargeUuid: java.util.UUID,

)

data class CreateSentenceRequest(

  @field:JsonProperty("prisonerId")
  val prisonerId: String,

  @field:JsonProperty("chargeUuids")
  val chargeUuids: List<java.util.UUID>,

)

data class CreateSentenceResponse(

  @field:JsonProperty("sentenceUuid")
  val sentenceUuid: String,

)

// TODO request endpoint to provide multiple ids for migration
// order must be returned to match body
data class CreateCourtCaseMigrationResponse(
  val courtCaseUuid: String,
  val appearances: List<AppearanceMapping>,
  // charges may span multiple appearances
  val charges: List<ChargeMapping>,
)

data class AppearanceMapping(
  val eventId: String,
  val appearanceUuid: String,
)

data class ChargeMapping(
  val offenderChargeId: String,
  val chargeUuid: String,
)

data class CreateCourtCaseMigrationRequest(
  val prisonerId: String,
  val appearances: List<CreateCourtAppearance>,
  val otherCaseReferences: List<CaseReference>,
)

// TODO presumably they will need the reference and a date time
data class CaseReference(
  val reference: String,
  @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
  val createDateTime: LocalDateTime,
)
