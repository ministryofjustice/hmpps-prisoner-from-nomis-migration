package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.profiledetails

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.model.ProfileDetailsPhysicalAttributesMigrationRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.model.ProfileDetailsPhysicalAttributesMigrationResponse

@Service
class ProfileDetailPhysicalAttributesDpsApiService(@Qualifier("prisonPersonApiWebClient") private val webClient: WebClient) {
  suspend fun migrateProfileDetailsPhysicalAttributes(
    prisonerNumber: String,
    requests: List<ProfileDetailsPhysicalAttributesMigrationRequest>,
  ): ProfileDetailsPhysicalAttributesMigrationResponse =
    webClient
      .put()
      .uri("/migration/prisoners/{prisonerNumber}/profile-details-physical-attributes", prisonerNumber)
      .bodyValue(requests)
      .retrieve()
      .awaitBody()
}