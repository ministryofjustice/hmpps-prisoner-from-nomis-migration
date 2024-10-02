package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson

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

  suspend fun createMappingsForMigration(mappings: ContactPersonMappingsDto): CreateMappingResult<PersonMappingDto> =
    webClient.post()
      .uri("/mapping/contact-person/migrate")
      .bodyValue(mappings)
      .retrieve()
      .bodyToMono(Unit::class.java)
      .map { CreateMappingResult<PersonMappingDto>() }
      .onErrorResume(WebClientResponseException.Conflict::class.java) {
        Mono.just(CreateMappingResult(it.getResponseBodyAs(object : ParameterizedTypeReference<DuplicateErrorResponse<PersonMappingDto>>() {})))
      }
      .awaitFirstOrDefault(CreateMappingResult())
}
