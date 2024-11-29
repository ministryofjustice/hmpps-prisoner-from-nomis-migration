package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson

import com.fasterxml.jackson.annotation.JsonProperty
import kotlinx.coroutines.reactive.awaitFirstOrDefault
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrNullWhenNotFound
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.CreateMappingResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.MigrationMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ContactPersonMappingsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonAddressMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonContactMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonEmailMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonMappingDto

@Service
class ContactPersonMappingApiService(@Qualifier("mappingApiWebClient") webClient: WebClient) : MigrationMapping<ContactPersonMappingsDto>(domainUrl = "/mapping/contact-person/person", webClient) {
  suspend fun getByNomisPersonIdOrNull(nomisPersonId: Long): PersonMappingDto? = webClient.get()
    .uri(
      "/mapping/contact-person/person/nomis-person-id/{nomisPersonId}",
      nomisPersonId,
    )
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun getByNomisContactIdOrNull(nomisContactId: Long): PersonContactMappingDto? = webClient.get()
    .uri(
      "/mapping/contact-person/contact/nomis-contact-id/{nomisContactId}",
      nomisContactId,
    )
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun getByNomisAddressIdOrNull(nomisAddressId: Long): PersonAddressMappingDto? = webClient.get()
    .uri(
      "/mapping/contact-person/address/nomis-address-id/{nomisAddressId}",
      nomisAddressId,
    )
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun getByNomisEmailIdOrNull(nomisInternetAddressId: Long): PersonEmailMappingDto? = webClient.get()
    .uri(
      "/mapping/contact-person/email/nomis-internet-address-id/{nomisAddressId}",
      nomisInternetAddressId,
    )
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun getByNomisPhoneIdOrNull(nomisPhoneId: Long): PersonPhoneMappingDto? = webClient.get()
    .uri(
      "/mapping/contact-person/phone/nomis-phone-id/{nomisPhoneId}",
      nomisPhoneId,
    )
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun createMappingsForMigration(mappings: ContactPersonMappingsDto): CreateMappingResult<PersonMappingDto> = webClient.post()
    .uri("/mapping/contact-person/migrate")
    .bodyValue(mappings)
    .retrieve()
    .bodyToMono(Unit::class.java)
    .map { CreateMappingResult<PersonMappingDto>() }
    .onErrorResume(WebClientResponseException.Conflict::class.java) {
      Mono.just(CreateMappingResult(it.getResponseBodyAs(object : ParameterizedTypeReference<DuplicateErrorResponse<PersonMappingDto>>() {})))
    }
    .awaitFirstOrDefault(CreateMappingResult())

  suspend fun createPersonMapping(mappings: PersonMappingDto): CreateMappingResult<PersonMappingDto> = webClient.post()
    .uri("/mapping/contact-person/person")
    .bodyValue(mappings)
    .retrieve()
    .bodyToMono(Unit::class.java)
    .map { CreateMappingResult<PersonMappingDto>() }
    .onErrorResume(WebClientResponseException.Conflict::class.java) {
      Mono.just(CreateMappingResult(it.getResponseBodyAs(object : ParameterizedTypeReference<DuplicateErrorResponse<PersonMappingDto>>() {})))
    }
    .awaitFirstOrDefault(CreateMappingResult())

  suspend fun createContactMapping(mappings: PersonContactMappingDto): CreateMappingResult<PersonContactMappingDto> = webClient.post()
    .uri("/mapping/contact-person/contact")
    .bodyValue(mappings)
    .retrieve()
    .bodyToMono(Unit::class.java)
    .map { CreateMappingResult<PersonContactMappingDto>() }
    .onErrorResume(WebClientResponseException.Conflict::class.java) {
      Mono.just(CreateMappingResult(it.getResponseBodyAs(object : ParameterizedTypeReference<DuplicateErrorResponse<PersonContactMappingDto>>() {})))
    }
    .awaitFirstOrDefault(CreateMappingResult())

  suspend fun createAddressMapping(mappings: PersonAddressMappingDto): CreateMappingResult<PersonAddressMappingDto> = webClient.post()
    .uri("/mapping/contact-person/address")
    .bodyValue(mappings)
    .retrieve()
    .bodyToMono(Unit::class.java)
    .map { CreateMappingResult<PersonAddressMappingDto>() }
    .onErrorResume(WebClientResponseException.Conflict::class.java) {
      Mono.just(CreateMappingResult(it.getResponseBodyAs(object : ParameterizedTypeReference<DuplicateErrorResponse<PersonAddressMappingDto>>() {})))
    }
    .awaitFirstOrDefault(CreateMappingResult())

  suspend fun createEmailMapping(mappings: PersonEmailMappingDto): CreateMappingResult<PersonEmailMappingDto> = webClient.post()
    .uri("/mapping/contact-person/email")
    .bodyValue(mappings)
    .retrieve()
    .bodyToMono(Unit::class.java)
    .map { CreateMappingResult<PersonEmailMappingDto>() }
    .onErrorResume(WebClientResponseException.Conflict::class.java) {
      Mono.just(CreateMappingResult(it.getResponseBodyAs(object : ParameterizedTypeReference<DuplicateErrorResponse<PersonEmailMappingDto>>() {})))
    }
    .awaitFirstOrDefault(CreateMappingResult())

  suspend fun createPhoneMapping(mappings: PersonPhoneMappingDto): CreateMappingResult<PersonPhoneMappingDto> = webClient.post()
    .uri("/mapping/contact-person/phone")
    .bodyValue(mappings)
    .retrieve()
    .bodyToMono(Unit::class.java)
    .map { CreateMappingResult<PersonPhoneMappingDto>() }
    .onErrorResume(WebClientResponseException.Conflict::class.java) {
      Mono.just(CreateMappingResult(it.getResponseBodyAs(object : ParameterizedTypeReference<DuplicateErrorResponse<PersonPhoneMappingDto>>() {})))
    }
    .awaitFirstOrDefault(CreateMappingResult())
}

// TODO replace with generated DTO once other PR is merged
data class PersonPhoneMappingDto(

  /* DPS id */
  @get:JsonProperty("dpsId")
  val dpsId: String,

  /* NOMIS id */
  @get:JsonProperty("nomisId")
  val nomisId: Long,

  @get:JsonProperty("mappingType")
  val mappingType: MappingType,

  @get:JsonProperty("dpsPhoneType")
  val dpsPhoneType: DpsPersonPhoneType,

  @get:JsonProperty("label")
  val label: String? = null,

  @get:JsonProperty("whenCreated")
  val whenCreated: String? = null,

) {

  /**
   *
   *
   * Values: MIGRATED,DPS_CREATED,NOMIS_CREATED
   */
  enum class MappingType(val value: String) {
    @JsonProperty(value = "MIGRATED")
    MIGRATED("MIGRATED"),

    @JsonProperty(value = "DPS_CREATED")
    DPS_CREATED("DPS_CREATED"),

    @JsonProperty(value = "NOMIS_CREATED")
    NOMIS_CREATED("NOMIS_CREATED"),
  }

  /**
   *
   *
   * Values: PERSON,ADDRESS
   */
  enum class DpsPersonPhoneType(val value: String) {
    @JsonProperty(value = "PERSON")
    PERSON("PERSON"),

    @JsonProperty(value = "ADDRESS")
    ADDRESS("ADDRESS"),
  }
}
