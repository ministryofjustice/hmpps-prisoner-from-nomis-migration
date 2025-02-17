package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodilessEntityIgnoreNotFound
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodilessEntityOrLogAndRethrowBadRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrLogAndRethrowBadRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.model.MigrateOrganisationRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.model.MigrateOrganisationResponse
import java.time.LocalDate
import java.time.LocalDateTime

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
      .awaitBodilessEntityIgnoreNotFound()
  }
  suspend fun createOrganisationAddress(organisationAddress: SyncCreateOrganisationAddressRequest): SyncCreateOrganisationAddressResponse = webClient.post()
    .uri("/sync/organisation-address")
    .bodyValue(organisationAddress)
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun updateOrganisationAddress(organisationAddressId: Long, organisationAddress: SyncUpdateOrganisationAddressRequest) {
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

  suspend fun createOrganisationPhone(organisationPhone: SyncCreateOrganisationPhoneRequest): SyncCreateOrganisationPhoneResponse = webClient.post()
    .uri("/sync/organisation-phone")
    .bodyValue(organisationPhone)
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun updateOrganisationPhone(organisationPhoneId: Long, organisationPhone: SyncUpdateOrganisationPhoneRequest) {
    webClient.put()
      .uri("/sync/organisation-phone/{organisationPhoneId}", organisationPhoneId)
      .bodyValue(organisationPhone)
      .retrieve()
      .awaitBodilessEntityOrLogAndRethrowBadRequest()
  }

  suspend fun createOrganisationAddressPhone(organisationAddressPhone: SyncCreateOrganisationAddressPhoneRequest): SyncCreateOrganisationAddressPhoneResponse = webClient.post()
    .uri("/sync/organisation-address-phone")
    .bodyValue(organisationAddressPhone)
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun updateOrganisationAddressPhone(organisationAddressPhoneId: Long, organisationAddressPhone: SyncUpdateOrganisationAddressPhoneRequest) {
    webClient.put()
      .uri("/sync/organisation-address-phone/{organisationAddressPhoneId}", organisationAddressPhoneId)
      .bodyValue(organisationAddressPhone)
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

data class SyncCreateOrganisationAddressRequest(
  val organisationId: Long,
  val primaryAddress: Boolean,
  val createdBy: String,
  val createdTime: LocalDateTime,
  val addressType: String? = null,
  val flat: String? = null,
  val `property`: String? = null,
  val street: String? = null,
  val area: String? = null,
  val cityCode: String? = null,
  val countyCode: String? = null,
  val postcode: String? = null,
  val countryCode: String? = null,
  val verified: Boolean? = null,
  val mailFlag: Boolean? = null,
  val startDate: LocalDate? = null,
  val endDate: LocalDate? = null,
  val noFixedAddress: Boolean? = null,
  val comments: String? = null,
  val specialNeedsCode: String? = null,
  val contactPersonName: String? = null,
  val businessHours: String? = null,
  val servicesAddress: Boolean? = null,
)

data class SyncCreateOrganisationAddressResponse(
  val organisationAddressId: Long,
)

data class SyncUpdateOrganisationAddressRequest(
  val primaryAddress: Boolean,
  val updatedBy: String,
  val updatedTime: LocalDateTime,
  val addressType: String? = null,
  val flat: String? = null,
  val `property`: String? = null,
  val street: String? = null,
  val area: String? = null,
  val cityCode: String? = null,
  val countyCode: String? = null,
  val postcode: String? = null,
  val countryCode: String? = null,
  val verified: Boolean? = null,
  val mailFlag: Boolean? = null,
  val startDate: LocalDate? = null,
  val endDate: LocalDate? = null,
  val noFixedAddress: Boolean? = null,
  val comments: String? = null,
  val specialNeedsCode: String? = null,
  val contactPersonName: String? = null,
  val businessHours: String? = null,
  val servicesAddress: Boolean? = null,
)

data class SyncCreateOrganisationPhoneRequest(
  val organisationId: Long,
  val phoneType: String,
  val phoneNumber: String,
  val createdBy: String,
  val createdTime: LocalDateTime,
  val extNumber: String? = null,
)

data class SyncCreateOrganisationPhoneResponse(
  val organisationPhoneId: Long,
)
data class SyncUpdateOrganisationPhoneRequest(
  val phoneType: String,
  val phoneNumber: String,
  val updatedBy: String,
  val updatedTime: LocalDateTime,
  val extNumber: String? = null,
)

data class SyncCreateOrganisationAddressPhoneRequest(
  val organisationId: Long,
  val organisationAddressId: Long,
  val phoneType: String,
  val phoneNumber: String,
  val createdBy: String,
  val createdTime: LocalDateTime,
  val extNumber: String? = null,
)
data class SyncUpdateOrganisationAddressPhoneRequest(
  val phoneType: String,
  val phoneNumber: String,
  val updatedBy: String,
  val updatedTime: LocalDateTime,
  val extNumber: String? = null,
)

data class SyncCreateOrganisationAddressPhoneResponse(
  val organisationAddressPhoneId: Long,
)
