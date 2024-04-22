package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing

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

@Service
class CourtSentencingDpsApiService(@Qualifier("courtSentencingApiWebClient") private val webClient: WebClient) {
  suspend fun createCourtCase(courtCase: CreateCourtCase): CreateCourtCaseResponse =
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
  suspend fun associateExistingCourtCharge(courtAppearanceId: String, charge: CreateCharge): CreateCourtAppearanceResponse =
    webClient
      .put()
      .uri("/court-appearance/{courtAppearanceId}/charge/{chargeId}", courtAppearanceId, charge.chargeUuid)
      .bodyValue(charge)
      .retrieve()
      .awaitBody()

  // add a court charge and associate it with the given appearance (will create sync mapping)
  suspend fun addNewCourtCharge(courtAppearanceId: String, charge: CreateCharge): CreateNewChargeResponse =
    webClient
      .put()
      .uri("/court-appearance/{courtAppearanceId}/charge/", courtAppearanceId)
      .bodyValue(charge)
      .retrieve()
      .awaitBody()
}

data class CreateNewChargeResponse(

  @field:JsonProperty("appearanceUuid")
  val chargeUuid: java.util.UUID,

)
