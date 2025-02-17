package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations

import kotlinx.coroutines.reactive.awaitFirstOrDefault
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.awaitBodilessEntity
import org.springframework.web.reactive.function.client.awaitBody
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrNullWhenNotFound
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.CreateMappingResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.MigrationMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorporateMappingsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.OrganisationsMappingDto

@Service
class OrganisationsMappingApiService(@Qualifier("mappingApiWebClient") webClient: WebClient) : MigrationMapping<CorporateMappingsDto>(domainUrl = "/mapping/corporate/organisation", webClient) {
  suspend fun createMappingsForMigration(mappings: CorporateMappingsDto): CreateMappingResult<OrganisationsMappingDto> = webClient.post()
    .uri("/mapping/corporate/migrate")
    .bodyValue(mappings)
    .retrieve()
    .bodyToMono(Unit::class.java)
    .map { CreateMappingResult<OrganisationsMappingDto>() }
    .onErrorResume(WebClientResponseException.Conflict::class.java) {
      Mono.just(CreateMappingResult(it.getResponseBodyAs(object : ParameterizedTypeReference<DuplicateErrorResponse<OrganisationsMappingDto>>() {})))
    }
    .awaitFirstOrDefault(CreateMappingResult())

  suspend fun createOrganisationMapping(mapping: OrganisationsMappingDto): CreateMappingResult<OrganisationsMappingDto> = createMapping("/mapping/corporate/organisation", mapping)

  suspend fun getByNomisCorporateIdOrNull(nomisCorporateId: Long): OrganisationsMappingDto? = webClient.get()
    .uri(
      "/mapping/corporate/organisation/nomis-corporate-id/{nomisCorporateId}",
      nomisCorporateId,
    )
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun getByNomisCorporateId(nomisCorporateId: Long): OrganisationsMappingDto = webClient.get()
    .uri(
      "/mapping/corporate/organisation/nomis-corporate-id/{nomisCorporateId}",
      nomisCorporateId,
    )
    .retrieve()
    .awaitBody()

  suspend fun deleteByNomisCorporateId(nomisCorporateId: Long) {
    webClient.delete()
      .uri(
        "/mapping/corporate/organisation/nomis-corporate-id/{nomisCorporateId}",
        nomisCorporateId,
      )
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun createAddressMapping(mapping: OrganisationsMappingDto): CreateMappingResult<OrganisationsMappingDto> = createMapping("/mapping/corporate/address", mapping)

  suspend fun getByNomisAddressIdOrNull(nomisAddressId: Long): OrganisationsMappingDto? = webClient.get()
    .uri(
      "/mapping/corporate/address/nomis-address-id/{nomisAddressId}",
      nomisAddressId,
    )
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun getByNomisAddressId(nomisAddressId: Long): OrganisationsMappingDto = webClient.get()
    .uri(
      "/mapping/corporate/address/nomis-address-id/{nomisAddressId}",
      nomisAddressId,
    )
    .retrieve()
    .awaitBody()

  suspend fun deleteByNomisAddressId(nomisAddressId: Long) {
    webClient.delete()
      .uri(
        "/mapping/corporate/address/nomis-address-id/{nomisAddressId}",
        nomisAddressId,
      )
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun createPhoneMapping(mapping: OrganisationsMappingDto): CreateMappingResult<OrganisationsMappingDto> = createMapping("/mapping/corporate/phone", mapping)

  suspend fun getByNomisPhoneIdOrNull(nomisPhoneId: Long): OrganisationsMappingDto? = webClient.get()
    .uri(
      "/mapping/corporate/phone/nomis-phone-id/{nomisPhoneId}",
      nomisPhoneId,
    )
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun getByNomisPhoneId(nomisPhoneId: Long): OrganisationsMappingDto = webClient.get()
    .uri(
      "/mapping/corporate/phone/nomis-phone-id/{nomisPhoneId}",
      nomisPhoneId,
    )
    .retrieve()
    .awaitBody()

  suspend fun deleteByNomisPhoneId(nomisPhoneId: Long) {
    webClient.delete()
      .uri(
        "/mapping/corporate/phone/nomis-phone-id/{nomisPhoneId}",
        nomisPhoneId,
      )
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun createAddressPhoneMapping(mapping: OrganisationsMappingDto): CreateMappingResult<OrganisationsMappingDto> = createMapping("/mapping/corporate/address-phone", mapping)

  suspend fun getByNomisAddressPhoneIdOrNull(nomisPhoneId: Long): OrganisationsMappingDto? = webClient.get()
    .uri(
      "/mapping/corporate/address-phone/nomis-phone-id/{nomisPhoneId}",
      nomisPhoneId,
    )
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun getByNomisAddressPhoneId(nomisPhoneId: Long): OrganisationsMappingDto = webClient.get()
    .uri(
      "/mapping/corporate/address-phone/nomis-phone-id/{nomisPhoneId}",
      nomisPhoneId,
    )
    .retrieve()
    .awaitBody()

  suspend fun deleteByNomisAddressPhoneId(nomisPhoneId: Long) {
    webClient.delete()
      .uri(
        "/mapping/corporate/address-phone/nomis-phone-id/{nomisPhoneId}",
        nomisPhoneId,
      )
      .retrieve()
      .awaitBodilessEntity()
  }

  private suspend inline fun <reified T : Any> createMapping(url: String, mapping: T): CreateMappingResult<OrganisationsMappingDto> = webClient.post()
    .uri(url)
    .bodyValue(mapping)
    .retrieve()
    .bodyToMono(Unit::class.java)
    .map { CreateMappingResult<OrganisationsMappingDto>() }
    .onErrorResume(WebClientResponseException.Conflict::class.java) {
      Mono.just(CreateMappingResult(it.getResponseBodyAs(object : ParameterizedTypeReference<DuplicateErrorResponse<OrganisationsMappingDto>>() {})))
    }
    .awaitFirstOrDefault(CreateMappingResult())
}
