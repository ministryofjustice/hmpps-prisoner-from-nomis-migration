package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip

import kotlinx.coroutines.reactive.awaitFirstOrDefault
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.awaitBodilessEntity
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrNullWhenNotFound
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.CreateMappingResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.MigrationMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CSIPFactorMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CSIPMappingDto

@Service
class CSIPMappingService(@Qualifier("mappingApiWebClient") webClient: WebClient) :
  MigrationMapping<CSIPMappingDto>(domainUrl = "/mapping/csip", webClient) {

  suspend fun getCSIPReportByNomisId(nomisCSIPReportId: Long): CSIPMappingDto? =
    webClient.get()
      .uri("/mapping/csip/nomis-csip-id/{nomisCSIPReportId}", nomisCSIPReportId)
      .retrieve()
      .awaitBodyOrNullWhenNotFound()

  suspend fun deleteCSIPReportMappingByDPSId(dpsCSIPReportId: String) {
    webClient.delete()
      .uri("/mapping/csip/dps-csip-id/{dpsCSIPReportId}", dpsCSIPReportId)
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun getCSIPFactorByNomisId(nomisCSIPFactorId: Long): CSIPFactorMappingDto? =
    webClient.get()
      .uri("/mapping/csip/factors/nomis-csip-factor-id/{nomisCSIPFactorId}", nomisCSIPFactorId)
      .retrieve()
      .awaitBodyOrNullWhenNotFound()

  suspend fun deleteCSIPFactorMappingByDPSId(dpsCSIPFactorId: String) {
    webClient.delete()
      .uri("/mapping/csip/factors/dps-csip-factor-id/{dpsCSIPFactorId}", dpsCSIPFactorId)
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun createCSIPFactorMapping(
    mapping: CSIPFactorMappingDto,
  ): CreateMappingResult<CSIPFactorMappingDto> {
    return webClient.post()
      .uri("/mapping/csip/factors")
      .bodyValue(
        mapping,
      )
      .retrieve()
      .bodyToMono(Unit::class.java)
      .map { CreateMappingResult<CSIPFactorMappingDto>() }
      .onErrorResume(WebClientResponseException.Conflict::class.java) {
        Mono.just(CreateMappingResult(it.getResponseBodyAs(object : ParameterizedTypeReference<DuplicateErrorResponse<CSIPFactorMappingDto>>() {})))
      }
      .awaitFirstOrDefault(CreateMappingResult())
  }
}
