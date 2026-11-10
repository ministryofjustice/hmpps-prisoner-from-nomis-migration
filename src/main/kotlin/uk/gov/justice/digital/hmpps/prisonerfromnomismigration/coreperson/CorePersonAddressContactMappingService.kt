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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.api.CorePersonAddressContactRepairResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.api.CorePersonAddressMappingResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.api.CorePersonContactMappingResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.api.CorePersonContactMappingResourceApi.NomisContactTypeDeleteCorePersonContactMappingByNomisId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.api.CorePersonContactMappingResourceApi.NomisContactTypeGetCorePersonContactMappingByNomisId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.api.CorePersonMigrationAddressContactResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonAddressContactMappingsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonAddressMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonAddressUsageMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonContactMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonMappingDto

@Service
class CorePersonAddressContactMappingService(@Qualifier("mappingApiWebClient") webClient: WebClient) : MigrationMapping<CorePersonAddressContactMappingsDto>("/mapping/core-person/address-contact-migration", webClient) {
  private val migrationApi = CorePersonMigrationAddressContactResourceApi(webClient)
  private val repairApi = CorePersonAddressContactRepairResourceApi(webClient)
  private val addressApi = CorePersonAddressMappingResourceApi(webClient)
  private val contactApi = CorePersonContactMappingResourceApi(webClient)

  suspend fun getCorePersonByPrisonNumberOrNull(prisonNumber: String): CorePersonMappingDto? = migrationApi
    .prepare(migrationApi.getAddressContactMappingByNomisPrisonNumberRequestConfig(prisonNumber))
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun getByNomisAddressIdOrNull(nomisAddressId: Long): CorePersonAddressMappingDto? = addressApi
    .prepare(addressApi.getCorePersonAddressMappingByNomisIdRequestConfig(nomisAddressId))
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun getByNomisAddressId(nomisAddressId: Long): CorePersonAddressMappingDto = addressApi
    .prepare(addressApi.getCorePersonAddressMappingByNomisIdRequestConfig(nomisAddressId))
    .retrieve()
    .awaitBody()

  suspend fun deleteByNomisAddressId(nomisAddressId: Long) {
    addressApi.deleteCorePersonAddressMappingByNomisId(nomisAddressId).awaitSingle()
  }

  suspend fun createAddressMapping(mapping: CorePersonAddressMappingDto): CreateMappingResult<CorePersonAddressMappingDto> = addressApi.createCorePersonAddressMapping(mapping)
    .map { CreateMappingResult<CorePersonAddressMappingDto>() }
    .onErrorResume(WebClientResponseException.Conflict::class.java) {
      Mono.just(CreateMappingResult(it.getResponseBodyAs(object : ParameterizedTypeReference<DuplicateErrorResponse<CorePersonAddressMappingDto>>() {})))
    }
    .awaitFirstOrDefault(CreateMappingResult())

  suspend fun getByNomisAddressUsageIdOrNull(nomisAddressId: Long, addressUsageCode: String): CorePersonAddressUsageMappingDto? = addressApi
    .prepare(addressApi.getCorePersonAddressUsageMappingByNomisIdRequestConfig(nomisAddressId, addressUsageCode))
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun getByNomisAddressUsageId(nomisAddressId: Long, addressUsageCode: String): CorePersonAddressUsageMappingDto = addressApi
    .prepare(addressApi.getCorePersonAddressUsageMappingByNomisIdRequestConfig(nomisAddressId, addressUsageCode))
    .retrieve()
    .awaitBody()

  suspend fun deleteByNomisAddressUsageId(nomisAddressId: Long, addressUsageCode: String) {
    addressApi.deleteCorePersonAddressUsageMappingByNomisId(nomisAddressId, addressUsageCode).awaitSingle()
  }

  suspend fun createAddressUsageMapping(mapping: CorePersonAddressUsageMappingDto): CreateMappingResult<CorePersonAddressUsageMappingDto> = addressApi.createCorePersonAddressUsageMapping(mapping)
    .map { CreateMappingResult<CorePersonAddressUsageMappingDto>() }
    .onErrorResume(WebClientResponseException.Conflict::class.java) {
      Mono.just(CreateMappingResult(it.getResponseBodyAs(object : ParameterizedTypeReference<DuplicateErrorResponse<CorePersonAddressUsageMappingDto>>() {})))
    }
    .awaitFirstOrDefault(CreateMappingResult())

  suspend fun getByNomisEmailIdOrNull(nomisInternetAddressId: Long): CorePersonContactMappingDto? = contactApi
    .prepare(contactApi.getCorePersonContactMappingByNomisIdRequestConfig(nomisInternetAddressId, NomisContactTypeGetCorePersonContactMappingByNomisId.EMAIL))
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun getByNomisEmailId(nomisInternetAddressId: Long): CorePersonContactMappingDto = contactApi
    .prepare(contactApi.getCorePersonContactMappingByNomisIdRequestConfig(nomisInternetAddressId, NomisContactTypeGetCorePersonContactMappingByNomisId.EMAIL))
    .retrieve()
    .awaitBody()

  suspend fun deleteByNomisEmailId(nomisInternetAddressId: Long) {
    contactApi.deleteCorePersonContactMappingByNomisId(nomisInternetAddressId, NomisContactTypeDeleteCorePersonContactMappingByNomisId.EMAIL).awaitSingle()
  }

  suspend fun createEmailMapping(mapping: CorePersonContactMappingDto): CreateMappingResult<CorePersonContactMappingDto> = contactApi.createCorePersonContactMapping(mapping)
    .map { CreateMappingResult<CorePersonContactMappingDto>() }
    .onErrorResume(WebClientResponseException.Conflict::class.java) {
      Mono.just(CreateMappingResult(it.getResponseBodyAs(object : ParameterizedTypeReference<DuplicateErrorResponse<CorePersonContactMappingDto>>() {})))
    }
    .awaitFirstOrDefault(CreateMappingResult())

  suspend fun getByNomisPhoneIdOrNull(nomisPhoneId: Long): CorePersonContactMappingDto? = contactApi
    .prepare(contactApi.getCorePersonContactMappingByNomisIdRequestConfig(nomisPhoneId, NomisContactTypeGetCorePersonContactMappingByNomisId.PHONE))
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun getByNomisPhoneId(nomisPhoneId: Long): CorePersonContactMappingDto = contactApi
    .prepare(contactApi.getCorePersonContactMappingByNomisIdRequestConfig(nomisPhoneId, NomisContactTypeGetCorePersonContactMappingByNomisId.PHONE))
    .retrieve()
    .awaitBody()

  suspend fun deleteByNomisPhoneId(nomisPhoneId: Long) {
    contactApi.deleteCorePersonContactMappingByNomisId(nomisPhoneId, NomisContactTypeDeleteCorePersonContactMappingByNomisId.PHONE).awaitSingle()
  }

  suspend fun createPhoneMapping(mapping: CorePersonContactMappingDto): CreateMappingResult<CorePersonContactMappingDto> = contactApi.createCorePersonContactMapping(mapping)
    .map { CreateMappingResult<CorePersonContactMappingDto>() }
    .onErrorResume(WebClientResponseException.Conflict::class.java) {
      Mono.just(CreateMappingResult(it.getResponseBodyAs(object : ParameterizedTypeReference<DuplicateErrorResponse<CorePersonContactMappingDto>>() {})))
    }
    .awaitFirstOrDefault(CreateMappingResult())

  suspend fun replaceMappings(mappings: CorePersonAddressContactMappingsDto) {
    repairApi.replaceCorePersonAddressContactMappings(mappings).awaitSingle()
  }
}
