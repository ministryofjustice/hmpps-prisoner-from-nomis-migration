package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

import kotlinx.coroutines.reactive.awaitFirstOrDefault
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.awaitBody
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrNullWhenNotFound
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.CreateMappingResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.MigrationMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.api.CorePersonMappingResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonAddressMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonEmailAddressMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonMappingsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonPhoneMappingDto

@Service
class CorePersonMappingService(@Qualifier("mappingApiWebClient") webClient: WebClient) : MigrationMapping<CorePersonMappingsDto>("/mapping/core-person", webClient) {
  private val api = CorePersonMappingResourceApi(webClient)

  suspend fun getCorePersonByPrisonNumberOrNull(prisonNumber: String): CorePersonMappingDto? = api
    .prepare(api.getPersonMappingByNomisPrisonNumberRequestConfig(prisonNumber))
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun getByNomisAddressIdOrNull(nomisAddressId: Long): CorePersonAddressMappingDto? = api
    .prepare(api.getCorePersonAddressMappingByNomisIdRequestConfig(nomisAddressId))
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun getByNomisAddressId(nomisAddressId: Long): CorePersonAddressMappingDto = api
    .prepare(api.getCorePersonAddressMappingByNomisIdRequestConfig(nomisAddressId))
    .retrieve()
    .awaitBody()

  suspend fun deleteByNomisAddressId(nomisAddressId: Long) {
    api.deleteCorePersonAddressMappingByNomisId(nomisAddressId).awaitSingle()
  }

  suspend fun createAddressMapping(mapping: CorePersonAddressMappingDto): CreateMappingResult<CorePersonAddressMappingDto> = api.createCorePersonAddressMapping(mapping)
    .map { CreateMappingResult<CorePersonAddressMappingDto>() }
    .onErrorResume(WebClientResponseException.Conflict::class.java) {
      Mono.just(CreateMappingResult(it.getResponseBodyAs(object : ParameterizedTypeReference<DuplicateErrorResponse<CorePersonAddressMappingDto>>() {})))
    }
    .awaitFirstOrDefault(CreateMappingResult())

  suspend fun getByNomisEmailIdOrNull(nomisInternetAddressId: Long): CorePersonEmailAddressMappingDto? = api
    .prepare(api.getCorePersonEmailMappingByNomisIdRequestConfig(nomisInternetAddressId))
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun getByNomisEmailId(nomisInternetAddressId: Long): CorePersonEmailAddressMappingDto = api
    .prepare(api.getCorePersonEmailMappingByNomisIdRequestConfig(nomisInternetAddressId))
    .retrieve()
    .awaitBody()

  suspend fun deleteByNomisEmailId(nomisInternetAddressId: Long) {
    api.deleteCorePersonEmailMappingByNomisId(nomisInternetAddressId).awaitSingle()
  }

  suspend fun createEmailMapping(mapping: CorePersonEmailAddressMappingDto): CreateMappingResult<CorePersonEmailAddressMappingDto> = api.createCorePersonEmailMapping(mapping)
    .map { CreateMappingResult<CorePersonEmailAddressMappingDto>() }
    .onErrorResume(WebClientResponseException.Conflict::class.java) {
      Mono.just(CreateMappingResult(it.getResponseBodyAs(object : ParameterizedTypeReference<DuplicateErrorResponse<CorePersonEmailAddressMappingDto>>() {})))
    }
    .awaitFirstOrDefault(CreateMappingResult())

  suspend fun getByNomisPhoneIdOrNull(nomisPhoneId: Long): CorePersonPhoneMappingDto? = api
    .prepare(api.getCorePersonPhoneMappingByNomisIdRequestConfig(nomisPhoneId))
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun getByNomisPhoneId(nomisPhoneId: Long): CorePersonPhoneMappingDto = api
    .prepare(api.getCorePersonPhoneMappingByNomisIdRequestConfig(nomisPhoneId))
    .retrieve()
    .awaitBody()

  suspend fun deleteByNomisPhoneId(nomisPhoneId: Long) {
    api.deleteCorePersonPhoneMappingByNomisId(nomisPhoneId).awaitSingle()
  }

  suspend fun createPhoneMapping(mapping: CorePersonPhoneMappingDto): CreateMappingResult<CorePersonPhoneMappingDto> = api.createCorePersonPhoneMapping(mapping)
    .map { CreateMappingResult<CorePersonPhoneMappingDto>() }
    .onErrorResume(WebClientResponseException.Conflict::class.java) {
      Mono.just(CreateMappingResult(it.getResponseBodyAs(object : ParameterizedTypeReference<DuplicateErrorResponse<CorePersonPhoneMappingDto>>() {})))
    }
    .awaitFirstOrDefault(CreateMappingResult())

  suspend fun replaceMappings(mappings: CorePersonMappingsDto) {
    api.replaceCorePersonMappings(mappings).awaitSingle()
  }
}
