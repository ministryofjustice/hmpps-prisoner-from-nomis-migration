package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBodilessEntity

@Service
class PrisonPersonNomisSyncApiService(@Qualifier("nomisSyncApiWebClient") private val webClient: WebClient) {
  suspend fun syncPhysicalAttributes(prisonerNumber: String) = webClient.put()
    .uri {
      it.path("/prisonperson/{prisonerNumber}/physical-attributes")
        // Note that we use the DPS field codes here because the nomis-update service transforms them to NOMIS codes
        .queryParam("fields", PHYSICAL_ATTRIBUTES_DPS_FIELDS)
        .build(prisonerNumber)
    }
    .retrieve()
    .awaitBodilessEntity()
}

private val PHYSICAL_ATTRIBUTES_DPS_FIELDS = listOf(
  "HEIGHT",
  "WEIGHT",
  "BUILD",
  "FACE",
  "FACIAL_HAIR",
  "HAIR",
  "LEFT_EYE_COLOUR",
  "RIGHT_EYE_COLOUR",
  "SHOE_SIZE",
)
