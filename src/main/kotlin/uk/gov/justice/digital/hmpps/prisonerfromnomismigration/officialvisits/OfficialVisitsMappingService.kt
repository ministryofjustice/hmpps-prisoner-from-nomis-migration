package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits

import kotlinx.coroutines.reactive.awaitFirstOrDefault
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrNullWhenNotFound
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.CreateMappingResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.MigrationMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.api.LocationMappingResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.api.OfficialVisitsResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.LocationMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.OfficialVisitMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.OfficialVisitMigrationMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.OfficialVisitorMappingDto

@Service
class OfficialVisitsMappingService(@Qualifier("mappingApiWebClient") webClient: WebClient) : MigrationMapping<OfficialVisitMigrationMappingDto>("/mapping/official-visits", webClient) {
  private val api = OfficialVisitsResourceApi(webClient)
  private val locationApi = LocationMappingResourceApi(webClient)

  suspend fun createVisitMapping(mapping: OfficialVisitMappingDto): CreateMappingResult<OfficialVisitMappingDto> = api.prepare(api.createVisitMappingRequestConfig(mapping))
    .retrieve()
    .bodyToMono<Unit>()
    .map { CreateMappingResult<OfficialVisitMappingDto>() }
    .onErrorResume(WebClientResponseException.Conflict::class.java) {
      Mono.just(CreateMappingResult(it.getResponseBodyAs(object : ParameterizedTypeReference<DuplicateErrorResponse<OfficialVisitMappingDto>>() {})))
    }
    .awaitFirstOrDefault(CreateMappingResult())

  suspend fun getByVisitNomisIdsOrNull(nomisVisitId: Long): OfficialVisitMappingDto? = api.prepare(
    api.getVisitMappingByNomisIdRequestConfig(
      nomisVisitId = nomisVisitId,
    ),
  )
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun createVisitorMapping(mapping: OfficialVisitorMappingDto): CreateMappingResult<OfficialVisitorMappingDto> = api.prepare(api.createVisitorMappingRequestConfig(mapping))
    .retrieve()
    .bodyToMono<Unit>()
    .map { CreateMappingResult<OfficialVisitorMappingDto>() }
    .onErrorResume(WebClientResponseException.Conflict::class.java) {
      Mono.just(CreateMappingResult(it.getResponseBodyAs(object : ParameterizedTypeReference<DuplicateErrorResponse<OfficialVisitorMappingDto>>() {})))
    }
    .awaitFirstOrDefault(CreateMappingResult())

  suspend fun getByVisitorNomisIdsOrNull(nomisVisitorId: Long): OfficialVisitorMappingDto? = api.prepare(
    api.getVisitorMappingByNomisIdRequestConfig(
      nomisVisitorId = nomisVisitorId,
    ),
  )
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun getInternalLocationByNomisId(nomisLocationId: Long): LocationMappingDto = locationApi.getMappingGivenNomisId1(nomisLocationId = nomisLocationId).awaitSingle()
}
