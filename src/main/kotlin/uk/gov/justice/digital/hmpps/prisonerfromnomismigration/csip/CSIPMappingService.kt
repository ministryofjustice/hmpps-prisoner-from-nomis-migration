package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBodilessEntity
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrNullWhenNotFound
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.MigrationMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CSIPFactorMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CSIPMappingDto

@Service
class CSIPMappingService(@Qualifier("mappingApiWebClient") webClient: WebClient) :
  MigrationMapping<CSIPMappingDto>(domainUrl = "/mapping/csip", webClient) {

  suspend fun findCSIPReportByNomisId(nomisCSIPReportId: Long): CSIPMappingDto? =
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

  suspend fun findCSIPFactorByNomisId(nomisCSIPFactorId: Long): CSIPFactorMappingDto? =
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
}
