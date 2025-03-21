package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.profiledetails

import kotlinx.coroutines.reactive.awaitFirstOrDefault
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.CreateMappingResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.MigrationMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ContactPersonProfileDetailsMigrationMappingDto

@Service
class ContactPersonProfileDetailsMappingApiService(@Qualifier("mappingApiWebClient") webClient: WebClient) : MigrationMapping<ContactPersonProfileDetailsMigrationMappingDto>(domainUrl = "/mapping/contact-person/profile-details/migration", webClient) {
  override suspend fun createMapping(
    mapping: ContactPersonProfileDetailsMigrationMappingDto,
    errorJavaClass: ParameterizedTypeReference<DuplicateErrorResponse<ContactPersonProfileDetailsMigrationMappingDto>>,
  ): CreateMappingResult<ContactPersonProfileDetailsMigrationMappingDto> = webClient.put()
    .uri(createMappingUrl())
    .bodyValue(
      mapping,
    )
    .retrieve()
    .bodyToMono(Unit::class.java)
    .map { CreateMappingResult<ContactPersonProfileDetailsMigrationMappingDto>() }
    .onErrorResume(WebClientResponseException.Conflict::class.java) {
      Mono.just(CreateMappingResult(it.getResponseBodyAs(errorJavaClass)))
    }
    .awaitFirstOrDefault(CreateMappingResult())
}
