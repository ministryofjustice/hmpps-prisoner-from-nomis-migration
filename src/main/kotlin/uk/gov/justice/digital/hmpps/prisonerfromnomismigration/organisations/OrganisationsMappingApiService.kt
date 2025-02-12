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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorporateAddressMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorporateMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorporateMappingsDto

@Service
class OrganisationsMappingApiService(@Qualifier("mappingApiWebClient") webClient: WebClient) : MigrationMapping<CorporateMappingsDto>(domainUrl = "/mapping/corporate/organisation", webClient) {
  suspend fun createMappingsForMigration(mappings: CorporateMappingsDto): CreateMappingResult<CorporateMappingDto> = webClient.post()
    .uri("/mapping/corporate/migrate")
    .bodyValue(mappings)
    .retrieve()
    .bodyToMono(Unit::class.java)
    .map { CreateMappingResult<CorporateMappingDto>() }
    .onErrorResume(WebClientResponseException.Conflict::class.java) {
      Mono.just(CreateMappingResult(it.getResponseBodyAs(object : ParameterizedTypeReference<DuplicateErrorResponse<CorporateMappingDto>>() {})))
    }
    .awaitFirstOrDefault(CreateMappingResult())

  suspend fun createOrganisationMapping(mapping: CorporateMappingDto): CreateMappingResult<CorporateMappingDto> = createMapping("/mapping/corporate/organisation", mapping)

  suspend fun getByNomisCorporateIdOrNull(nomisCorporateId: Long): CorporateMappingDto? = webClient.get()
    .uri(
      "/mapping/corporate/organisation/nomis-corporate-id/{nomisCorporateId}",
      nomisCorporateId,
    )
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun getByNomisCorporateId(nomisCorporateId: Long): CorporateMappingDto = webClient.get()
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

  suspend fun createAddressMapping(mapping: CorporateAddressMappingDto): CreateMappingResult<CorporateAddressMappingDto> = createMapping("/mapping/corporate/address", mapping)

  suspend fun getByNomisAddressIdOrNull(nomisAddressId: Long): CorporateAddressMappingDto? = webClient.get()
    .uri(
      "/mapping/corporate/address/nomis-address-id/{nomisAddressId}",
      nomisAddressId,
    )
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun getByNomisAddressId(nomisAddressId: Long): CorporateAddressMappingDto = webClient.get()
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

  private suspend inline fun <reified T : Any> createMapping(url: String, mapping: T): CreateMappingResult<T> = webClient.post()
    .uri(url)
    .bodyValue(mapping)
    .retrieve()
    .bodyToMono(Unit::class.java)
    .map { CreateMappingResult<T>() }
    .onErrorResume(WebClientResponseException.Conflict::class.java) {
      Mono.just(CreateMappingResult(it.getResponseBodyAs(object : ParameterizedTypeReference<DuplicateErrorResponse<T>>() {})))
    }
    .awaitFirstOrDefault(CreateMappingResult())
}
