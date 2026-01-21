package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrNullWhenNotFound
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

  suspend fun getByVisitNomisIdsOrNull(nomisVisitId: Long): OfficialVisitMappingDto? = api.prepare(
    api.getVisitMappingByNomisIdRequestConfig(
      nomisVisitId = nomisVisitId,
    ),
  )
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun getByVisitorNomisIdsOrNull(nomisVisitorId: Long): OfficialVisitorMappingDto? = api.prepare(
    api.getVisitorMappingByNomisIdRequestConfig(
      nomisVisitorId = nomisVisitorId,
    ),
  )
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun getInternalLocationByNomisId(nomisLocationId: Long): LocationMappingDto = locationApi.getMappingGivenNomisId1(nomisLocationId = nomisLocationId).awaitSingle()
}
