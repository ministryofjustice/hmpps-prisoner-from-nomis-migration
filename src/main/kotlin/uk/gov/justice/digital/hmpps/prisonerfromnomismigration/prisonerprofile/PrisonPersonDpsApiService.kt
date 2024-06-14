package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonerprofile

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.model.PhysicalAttributesDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.model.UpdatePhysicalAttributesRequest

@Service
class PrisonPersonDpsApiService(@Qualifier("prisonPersonApiWebClient") private val webClient: WebClient) {
  // TODO SDIT-1816 I've made an educated guess at the endpoint which doesn't exist yet. When it's ready fix this and write some unit tests
  suspend fun syncPhysicalAttributes(
    prisonerNumber: String,
    heightCentimetres: Int,
    weightKilograms: Int,
  ): PhysicalAttributesDto =
    webClient
      .put()
      .uri("/sync/prisoners/{prisonerNumber}/physical-attributes", prisonerNumber)
      .bodyValue(updatePhysicalAttributesRequest(heightCentimetres, weightKilograms))
      .retrieve()
      .awaitBody()

  private fun updatePhysicalAttributesRequest(heightCentimetres: Int, weightKilograms: Int) =
    UpdatePhysicalAttributesRequest(height = heightCentimetres, weight = weightKilograms)
}
