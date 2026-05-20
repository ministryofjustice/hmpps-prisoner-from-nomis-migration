package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodilessEntityIgnoreNotFound
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodilessEntityOrLogAndRethrowBadRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrLogAndRethrowBadRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.model.MigrateOrganisationRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.model.MigrateOrganisationResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.model.SyncAddressPhoneResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.model.SyncAddressResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.model.SyncCreateAddressPhoneRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.model.SyncCreateAddressRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.model.SyncCreateEmailRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.model.SyncCreateOrganisationRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.model.SyncCreatePhoneRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.model.SyncCreateWebRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.model.SyncEmailResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.model.SyncOrganisationResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.model.SyncPhoneResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.model.SyncUpdateAddressPhoneRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.model.SyncUpdateAddressRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.model.SyncUpdateEmailRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.model.SyncUpdateOrganisationRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.model.SyncUpdatePhoneRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.model.SyncUpdateTypesRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.model.SyncUpdateWebRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.model.SyncWebResponse

@Service
class OrganisationsDpsApiService(@Qualifier("organisationsDpsApiWebClient") private val webClient: WebClient) {
  suspend fun migrateOrganisation(organisation: MigrateOrganisationRequest): MigrateOrganisationResponse = webClient.post()
    .uri("/migrate/organisation")
    .bodyValue(organisation)
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun createOrganisation(organisation: SyncCreateOrganisationRequest): SyncOrganisationResponse = webClient.post()
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
      .awaitBodilessEntityIgnoreNotFound()
  }
  suspend fun createOrganisationAddress(organisationAddress: SyncCreateAddressRequest): SyncAddressResponse = webClient.post()
    .uri("/sync/organisation-address")
    .bodyValue(organisationAddress)
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun updateOrganisationAddress(organisationAddressId: Long, organisationAddress: SyncUpdateAddressRequest) {
    webClient.put()
      .uri("/sync/organisation-address/{organisationAddressId}", organisationAddressId)
      .bodyValue(organisationAddress)
      .retrieve()
      .awaitBodilessEntityOrLogAndRethrowBadRequest()
  }

  suspend fun deleteOrganisationAddress(organisationAddressId: Long) {
    webClient.delete()
      .uri("/sync/organisation-address/{organisationAddressId}", organisationAddressId)
      .retrieve()
      .awaitBodilessEntityIgnoreNotFound()
  }

  suspend fun createOrganisationPhone(organisationPhone: SyncCreatePhoneRequest): SyncPhoneResponse = webClient.post()
    .uri("/sync/organisation-phone")
    .bodyValue(organisationPhone)
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun updateOrganisationPhone(organisationPhoneId: Long, organisationPhone: SyncUpdatePhoneRequest) {
    webClient.put()
      .uri("/sync/organisation-phone/{organisationPhoneId}", organisationPhoneId)
      .bodyValue(organisationPhone)
      .retrieve()
      .awaitBodilessEntityOrLogAndRethrowBadRequest()
  }
  suspend fun deleteOrganisationPhone(organisationPhoneId: Long) {
    webClient.delete()
      .uri("/sync/organisation-phone/{organisationPhoneId}", organisationPhoneId)
      .retrieve()
      .awaitBodilessEntityIgnoreNotFound()
  }

  suspend fun createOrganisationAddressPhone(organisationAddressPhone: SyncCreateAddressPhoneRequest): SyncAddressPhoneResponse = webClient.post()
    .uri("/sync/organisation-address-phone")
    .bodyValue(organisationAddressPhone)
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun updateOrganisationAddressPhone(organisationAddressPhoneId: Long, organisationAddressPhone: SyncUpdateAddressPhoneRequest) {
    webClient.put()
      .uri("/sync/organisation-address-phone/{organisationAddressPhoneId}", organisationAddressPhoneId)
      .bodyValue(organisationAddressPhone)
      .retrieve()
      .awaitBodilessEntityOrLogAndRethrowBadRequest()
  }
  suspend fun deleteOrganisationAddressPhone(organisationAddressPhoneId: Long) {
    webClient.delete()
      .uri("/sync/organisation-address-phone/{organisationAddressPhoneId}", organisationAddressPhoneId)
      .retrieve()
      .awaitBodilessEntityIgnoreNotFound()
  }
  suspend fun createOrganisationWebAddress(organisationWebAddress: SyncCreateWebRequest): SyncWebResponse = webClient.post()
    .uri("/sync/organisation-web")
    .bodyValue(organisationWebAddress)
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun updateOrganisationWebAddress(organisationWebAddressId: Long, organisationWebAddress: SyncUpdateWebRequest) {
    webClient.put()
      .uri("/sync/organisation-web/{organisationWebAddressId}", organisationWebAddressId)
      .bodyValue(organisationWebAddress)
      .retrieve()
      .awaitBodilessEntityOrLogAndRethrowBadRequest()
  }
  suspend fun deleteOrganisationWebAddress(organisationWebAddressId: Long) {
    webClient.delete()
      .uri("/sync/organisation-web/{organisationWebAddressId}", organisationWebAddressId)
      .retrieve()
      .awaitBodilessEntityIgnoreNotFound()
  }

  suspend fun createOrganisationEmail(organisationEmail: SyncCreateEmailRequest): SyncEmailResponse = webClient.post()
    .uri("/sync/organisation-email")
    .bodyValue(organisationEmail)
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun updateOrganisationEmail(organisationEmailId: Long, organisationEmail: SyncUpdateEmailRequest) {
    webClient.put()
      .uri("/sync/organisation-email/{organisationEmailId}", organisationEmailId)
      .bodyValue(organisationEmail)
      .retrieve()
      .awaitBodilessEntityOrLogAndRethrowBadRequest()
  }
  suspend fun deleteOrganisationEmail(organisationEmailId: Long) {
    webClient.delete()
      .uri("/sync/organisation-email/{organisationEmailId}", organisationEmailId)
      .retrieve()
      .awaitBodilessEntityIgnoreNotFound()
  }
  suspend fun updateOrganisationTypes(organisationId: Long, organisationTypes: SyncUpdateTypesRequest) {
    webClient.put()
      .uri("/sync/organisation-types/{organisationId}", organisationId)
      .bodyValue(organisationTypes)
      .retrieve()
      .awaitBodilessEntityOrLogAndRethrowBadRequest()
  }
}
