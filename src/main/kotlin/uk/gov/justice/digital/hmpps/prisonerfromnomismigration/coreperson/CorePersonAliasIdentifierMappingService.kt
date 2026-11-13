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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.api.CorePersonAliasIdentifierRepairResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.api.CorePersonAliasMappingResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.api.CorePersonIdentifierMappingResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.api.CorePersonMigrationAliasIdentifierResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonAliasIdentifierMappingsByNomisIdsRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonAliasIdentifierMappingsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonAliasIdentifierMappingsResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.OffenderAliasMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.OffenderIdentifierMappingDto

@Service
class CorePersonAliasIdentifierMappingService(@Qualifier("mappingApiWebClient") webClient: WebClient) : MigrationMapping<CorePersonAliasIdentifierMappingsDto>("/mapping/core-person/alias-identifier-migration", webClient) {
  private val migrationApi = CorePersonMigrationAliasIdentifierResourceApi(webClient)
  private val repairApi = CorePersonAliasIdentifierRepairResourceApi(webClient)
  private val aliasApi = CorePersonAliasMappingResourceApi(webClient)
  private val identifierApi = CorePersonIdentifierMappingResourceApi(webClient)

  suspend fun getCorePersonByPrisonNumberOrNull(prisonNumber: String): CorePersonMappingDto? = migrationApi
    .prepare(migrationApi.getAliasIdentifierMappingByNomisPrisonNumberRequestConfig(prisonNumber))
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun getByNomisAliasIdOrNull(nomisOffenderId: Long): OffenderAliasMappingDto? = aliasApi
    .prepare(aliasApi.getOffenderAliasMappingByNomisIdRequestConfig(nomisOffenderId))
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun getByNomisAliasId(nomisOffenderId: Long): OffenderAliasMappingDto = aliasApi
    .prepare(aliasApi.getOffenderAliasMappingByNomisIdRequestConfig(nomisOffenderId))
    .retrieve()
    .awaitBody()

  suspend fun deleteByNomisAliasId(nomisOffenderId: Long) {
    aliasApi.deleteOffenderAliasMappingByNomisId(nomisOffenderId).awaitSingle()
  }

  suspend fun createAliasMapping(mapping: OffenderAliasMappingDto): CreateMappingResult<OffenderAliasMappingDto> = aliasApi.createOffenderAliasMappingWithHttpInfo(mapping)
    .map { CreateMappingResult<OffenderAliasMappingDto>() }
    .onErrorResume(WebClientResponseException.Conflict::class.java) {
      Mono.just(CreateMappingResult(it.getResponseBodyAs(object : ParameterizedTypeReference<DuplicateErrorResponse<OffenderAliasMappingDto>>() {})))
    }
    .awaitFirstOrDefault(CreateMappingResult())

  suspend fun getByNomisIdentifierIdOrNull(nomisOffenderId: Long, nomisIdentifierSequence: Int): OffenderIdentifierMappingDto? = identifierApi
    .prepare(identifierApi.getOffenderIdentifierMappingByNomisIdRequestConfig(nomisOffenderId, nomisIdentifierSequence))
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun getByNomisIdentifierId(nomisOffenderId: Long, nomisIdentifierSequence: Int): OffenderIdentifierMappingDto = identifierApi
    .prepare(identifierApi.getOffenderIdentifierMappingByNomisIdRequestConfig(nomisOffenderId, nomisIdentifierSequence))
    .retrieve()
    .awaitBody()

  suspend fun getIdentifierMappingsByNomisOffenderId(nomisOffenderId: Long): List<OffenderIdentifierMappingDto> = identifierApi
    .getOffenderIdentifierMappingsByNomisOffenderId(nomisOffenderId)
    .awaitSingle()

  suspend fun replaceIdentifierMappings(nomisOffenderId: Long, mappings: List<OffenderIdentifierMappingDto>) {
    identifierApi.replaceOffenderIdentifierMappings(nomisOffenderId, mappings).awaitSingle()
  }

  suspend fun deleteByNomisIdentifierId(nomisOffenderId: Long, nomisIdentifierSequence: Int) {
    identifierApi.deleteOffenderIdentifierMappingByNomisId(nomisOffenderId, nomisIdentifierSequence).awaitSingle()
  }

  suspend fun createIdentifierMapping(mapping: OffenderIdentifierMappingDto): CreateMappingResult<OffenderIdentifierMappingDto> = identifierApi.createOffenderIdentifierMapping(mapping)
    .map { CreateMappingResult<OffenderIdentifierMappingDto>() }
    .onErrorResume(WebClientResponseException.Conflict::class.java) {
      Mono.just(CreateMappingResult(it.getResponseBodyAs(object : ParameterizedTypeReference<DuplicateErrorResponse<OffenderIdentifierMappingDto>>() {})))
    }
    .awaitFirstOrDefault(CreateMappingResult())

  suspend fun getMappingsFromNomisIds(request: CorePersonAliasIdentifierMappingsByNomisIdsRequest): CorePersonAliasIdentifierMappingsResult = repairApi
    .prepare(repairApi.getMappingsFromNomisIdsForAliasIdentifierRequestConfig(request))
    .retrieve()
    .awaitBody()

  suspend fun replaceMappings(mappings: CorePersonAliasIdentifierMappingsDto) {
    repairApi.replaceCorePersonAliasIdentifierMappings(mappings).awaitSingle()
  }
}
