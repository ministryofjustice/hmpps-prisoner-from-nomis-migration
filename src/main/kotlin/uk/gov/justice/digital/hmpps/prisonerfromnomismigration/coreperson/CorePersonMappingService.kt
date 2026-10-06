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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.api.CorePersonMappingResourceApi.NomisContactTypeDeleteCorePersonContactMappingByNomisId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.api.CorePersonMappingResourceApi.NomisContactTypeGetCorePersonContactMappingByNomisId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonAddressMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonAddressUsageMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonContactMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonMappingsDto

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

  suspend fun getByNomisAddressUsageIdOrNull(nomisAddressId: Long, addressUsageCode: String): CorePersonAddressUsageMappingDto? = api
    .prepare(api.getCorePersonAddressUsageMappingByNomisIdRequestConfig(nomisAddressId, addressUsageCode))
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun getByNomisAddressUsageId(nomisAddressId: Long, addressUsageCode: String): CorePersonAddressUsageMappingDto = api
    .prepare(api.getCorePersonAddressUsageMappingByNomisIdRequestConfig(nomisAddressId, addressUsageCode))
    .retrieve()
    .awaitBody()

  suspend fun deleteByNomisAddressUsageId(nomisAddressId: Long, addressUsageCode: String) {
    api.deleteCorePersonAddressUsageMappingByNomisId(nomisAddressId, addressUsageCode).awaitSingle()
  }

  suspend fun createAddressUsageMapping(mapping: CorePersonAddressUsageMappingDto): CreateMappingResult<CorePersonAddressUsageMappingDto> = api.createCorePersonAddressUsageMapping(mapping)
    .map { CreateMappingResult<CorePersonAddressUsageMappingDto>() }
    .onErrorResume(WebClientResponseException.Conflict::class.java) {
      Mono.just(CreateMappingResult(it.getResponseBodyAs(object : ParameterizedTypeReference<DuplicateErrorResponse<CorePersonAddressUsageMappingDto>>() {})))
    }
    .awaitFirstOrDefault(CreateMappingResult())

  suspend fun getByNomisEmailIdOrNull(nomisInternetAddressId: Long): CorePersonContactMappingDto? = api
    .prepare(api.getCorePersonContactMappingByNomisIdRequestConfig(nomisInternetAddressId, NomisContactTypeGetCorePersonContactMappingByNomisId.EMAIL))
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun getByNomisEmailId(nomisInternetAddressId: Long): CorePersonContactMappingDto = api
    .prepare(api.getCorePersonContactMappingByNomisIdRequestConfig(nomisInternetAddressId, NomisContactTypeGetCorePersonContactMappingByNomisId.EMAIL))
    .retrieve()
    .awaitBody()

  suspend fun deleteByNomisEmailId(nomisInternetAddressId: Long) {
    api.deleteCorePersonContactMappingByNomisId(nomisInternetAddressId, NomisContactTypeDeleteCorePersonContactMappingByNomisId.EMAIL).awaitSingle()
  }

  suspend fun createEmailMapping(mapping: CorePersonContactMappingDto): CreateMappingResult<CorePersonContactMappingDto> = api.createCorePersonContactMapping(mapping)
    .map { CreateMappingResult<CorePersonContactMappingDto>() }
    .onErrorResume(WebClientResponseException.Conflict::class.java) {
      Mono.just(CreateMappingResult(it.getResponseBodyAs(object : ParameterizedTypeReference<DuplicateErrorResponse<CorePersonContactMappingDto>>() {})))
    }
    .awaitFirstOrDefault(CreateMappingResult())

  suspend fun getByNomisPhoneIdOrNull(nomisPhoneId: Long): CorePersonContactMappingDto? = api
    .prepare(api.getCorePersonContactMappingByNomisIdRequestConfig(nomisPhoneId, NomisContactTypeGetCorePersonContactMappingByNomisId.PHONE))
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun getByNomisPhoneId(nomisPhoneId: Long): CorePersonContactMappingDto = api
    .prepare(api.getCorePersonContactMappingByNomisIdRequestConfig(nomisPhoneId, NomisContactTypeGetCorePersonContactMappingByNomisId.PHONE))
    .retrieve()
    .awaitBody()

  suspend fun deleteByNomisPhoneId(nomisPhoneId: Long) {
    api.deleteCorePersonContactMappingByNomisId(nomisPhoneId, NomisContactTypeDeleteCorePersonContactMappingByNomisId.PHONE).awaitSingle()
  }

  suspend fun createPhoneMapping(mapping: CorePersonContactMappingDto): CreateMappingResult<CorePersonContactMappingDto> = api.createCorePersonContactMapping(mapping)
    .map { CreateMappingResult<CorePersonContactMappingDto>() }
    .onErrorResume(WebClientResponseException.Conflict::class.java) {
      Mono.just(CreateMappingResult(it.getResponseBodyAs(object : ParameterizedTypeReference<DuplicateErrorResponse<CorePersonContactMappingDto>>() {})))
    }
    .awaitFirstOrDefault(CreateMappingResult())

  suspend fun replaceMappings(mappings: CorePersonMappingsDto) {
    api.replaceCorePersonMappings(mappings).awaitSingle()
  }
}
