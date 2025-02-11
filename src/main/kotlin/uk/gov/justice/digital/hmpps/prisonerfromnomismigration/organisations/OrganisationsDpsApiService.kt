package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodilessEntityOrLogAndRethrowBadRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrLogAndRethrowBadRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.model.MigrateOrganisationRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.model.MigrateOrganisationResponse
import java.time.LocalDate

@Service
class OrganisationsDpsApiService(@Qualifier("organisationsDpsApiWebClient") private val webClient: WebClient) {
  suspend fun migrateOrganisation(organisation: MigrateOrganisationRequest): MigrateOrganisationResponse = webClient.post()
    .uri("/migrate/organisation")
    .bodyValue(organisation)
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun createOrganisation(organisation: SyncCreateOrganisationRequest): SyncCreateOrganisationResponse = webClient.post()
    .uri("/sync/organisation")
    .bodyValue(organisation)
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun updateOrganisation(organisationId: Long, organisation: SyncUpdateOrganisationRequest) {
    webClient.put()
      .uri("/sync/organisation/{organisationId}", organisationId)
      .bodyValue(organisation)
      .retrieve()
      .awaitBodilessEntityOrLogAndRethrowBadRequest()
  }

  suspend fun deleteOrganisation(organisationId: Long) {
    webClient.delete()
      .uri("/sync/organisation/{organisationId}", organisationId)
      .retrieve()
      .awaitBodilessEntityOrLogAndRethrowBadRequest()
  }
}

//  Fake DTOs - replace with real ones once created
data class SyncCreateOrganisationRequest(
  val organisationId: Long,
  val organisationName: String,
  val programmeNumber: String? = null,
  val vatNumber: String? = null,
  val caseloadId: String? = null,
  val comments: String? = null,
  val active: Boolean,
  val deactivatedDate: LocalDate? = null,
)

data class SyncUpdateOrganisationRequest(
  val organisationName: String,
  val programmeNumber: String? = null,
  val vatNumber: String? = null,
  val caseloadId: String? = null,
  val comments: String? = null,
  val active: Boolean,
  val deactivatedDate: LocalDate? = null,
)

data class SyncCreateOrganisationResponse(
  val organisationId: Long,
)
