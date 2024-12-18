package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson

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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ContactPersonMappingsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonAddressMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonContactMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonContactRestrictionMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonEmailMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonIdentifierMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonPhoneMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonRestrictionMappingDto

@Service
class ContactPersonMappingApiService(@Qualifier("mappingApiWebClient") webClient: WebClient) : MigrationMapping<ContactPersonMappingsDto>(domainUrl = "/mapping/contact-person/person", webClient) {
  suspend fun getByNomisPersonIdOrNull(nomisPersonId: Long): PersonMappingDto? = webClient.get()
    .uri(
      "/mapping/contact-person/person/nomis-person-id/{nomisPersonId}",
      nomisPersonId,
    )
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun getByNomisPersonId(nomisPersonId: Long): PersonMappingDto = webClient.get()
    .uri(
      "/mapping/contact-person/person/nomis-person-id/{nomisPersonId}",
      nomisPersonId,
    )
    .retrieve()
    .awaitBody()

  suspend fun getByNomisContactIdOrNull(nomisContactId: Long): PersonContactMappingDto? = webClient.get()
    .uri(
      "/mapping/contact-person/contact/nomis-contact-id/{nomisContactId}",
      nomisContactId,
    )
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun getByNomisContactId(nomisContactId: Long): PersonContactMappingDto = webClient.get()
    .uri(
      "/mapping/contact-person/contact/nomis-contact-id/{nomisContactId}",
      nomisContactId,
    )
    .retrieve()
    .awaitBody()

  suspend fun getByNomisAddressIdOrNull(nomisAddressId: Long): PersonAddressMappingDto? = webClient.get()
    .uri(
      "/mapping/contact-person/address/nomis-address-id/{nomisAddressId}",
      nomisAddressId,
    )
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun getByNomisAddressId(nomisAddressId: Long): PersonAddressMappingDto = webClient.get()
    .uri(
      "/mapping/contact-person/address/nomis-address-id/{nomisAddressId}",
      nomisAddressId,
    )
    .retrieve()
    .awaitBody()

  suspend fun getByNomisEmailIdOrNull(nomisInternetAddressId: Long): PersonEmailMappingDto? = webClient.get()
    .uri(
      "/mapping/contact-person/email/nomis-internet-address-id/{nomisAddressId}",
      nomisInternetAddressId,
    )
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun getByNomisEmailId(nomisInternetAddressId: Long): PersonEmailMappingDto = webClient.get()
    .uri(
      "/mapping/contact-person/email/nomis-internet-address-id/{nomisAddressId}",
      nomisInternetAddressId,
    )
    .retrieve()
    .awaitBody()

  suspend fun getByNomisPhoneIdOrNull(nomisPhoneId: Long): PersonPhoneMappingDto? = webClient.get()
    .uri(
      "/mapping/contact-person/phone/nomis-phone-id/{nomisPhoneId}",
      nomisPhoneId,
    )
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun getByNomisPhoneId(nomisPhoneId: Long): PersonPhoneMappingDto = webClient.get()
    .uri(
      "/mapping/contact-person/phone/nomis-phone-id/{nomisPhoneId}",
      nomisPhoneId,
    )
    .retrieve()
    .awaitBody()

  suspend fun deleteByNomisPhoneId(nomisPhoneId: Long) {
    webClient.delete()
      .uri(
        "/mapping/contact-person/phone/nomis-phone-id/{nomisPhoneId}",
        nomisPhoneId,
      )
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun getByNomisIdentifierIdsOrNull(nomisPersonId: Long, nomisSequenceNumber: Long): PersonIdentifierMappingDto? = webClient.get()
    .uri(
      "/mapping/contact-person/identifier/nomis-person-id/{nomisPersonId}/nomis-sequence-number/{nomisSequenceNumber}",
      nomisPersonId,
      nomisSequenceNumber,
    )
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun getByNomisIdentifierIds(nomisPersonId: Long, nomisSequenceNumber: Long): PersonIdentifierMappingDto = webClient.get()
    .uri(
      "/mapping/contact-person/identifier/nomis-person-id/{nomisPersonId}/nomis-sequence-number/{nomisSequenceNumber}",
      nomisPersonId,
      nomisSequenceNumber,
    )
    .retrieve()
    .awaitBody()

  suspend fun getByNomisContactRestrictionIdOrNull(nomisContactRestrictionId: Long): PersonContactRestrictionMappingDto? = webClient.get()
    .uri(
      "/mapping/contact-person/contact-restriction/nomis-contact-restriction-id/{nomisContactRestrictionId}",
      nomisContactRestrictionId,
    )
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun getByNomisPersonRestrictionIdOrNull(nomisPersonRestrictionId: Long): PersonRestrictionMappingDto? = webClient.get()
    .uri(
      "/mapping/contact-person/person-restriction/nomis-person-restriction-id/{nomisPersonRestrictionId}",
      nomisPersonRestrictionId,
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

  suspend fun createIdentifierMapping(mappings: PersonIdentifierMappingDto): CreateMappingResult<PersonIdentifierMappingDto> = webClient.post()
    .uri("/mapping/contact-person/identifier")
    .bodyValue(mappings)
    .retrieve()
    .bodyToMono(Unit::class.java)
    .map { CreateMappingResult<PersonIdentifierMappingDto>() }
    .onErrorResume(WebClientResponseException.Conflict::class.java) {
      Mono.just(CreateMappingResult(it.getResponseBodyAs(object : ParameterizedTypeReference<DuplicateErrorResponse<PersonIdentifierMappingDto>>() {})))
    }
    .awaitFirstOrDefault(CreateMappingResult())

  suspend fun createContactRestrictionMapping(mappings: PersonContactRestrictionMappingDto): CreateMappingResult<PersonContactRestrictionMappingDto> = webClient.post()
    .uri("/mapping/contact-person/contact-restriction")
    .bodyValue(mappings)
    .retrieve()
    .bodyToMono(Unit::class.java)
    .map { CreateMappingResult<PersonContactRestrictionMappingDto>() }
    .onErrorResume(WebClientResponseException.Conflict::class.java) {
      Mono.just(CreateMappingResult(it.getResponseBodyAs(object : ParameterizedTypeReference<DuplicateErrorResponse<PersonContactRestrictionMappingDto>>() {})))
    }
    .awaitFirstOrDefault(CreateMappingResult())

  suspend fun createPersonRestrictionMapping(mappings: PersonRestrictionMappingDto): CreateMappingResult<PersonRestrictionMappingDto> = webClient.post()
    .uri("/mapping/contact-person/person-restriction")
    .bodyValue(mappings)
    .retrieve()
    .bodyToMono(Unit::class.java)
    .map { CreateMappingResult<PersonRestrictionMappingDto>() }
    .onErrorResume(WebClientResponseException.Conflict::class.java) {
      Mono.just(CreateMappingResult(it.getResponseBodyAs(object : ParameterizedTypeReference<DuplicateErrorResponse<PersonRestrictionMappingDto>>() {})))
    }
    .awaitFirstOrDefault(CreateMappingResult())
}
