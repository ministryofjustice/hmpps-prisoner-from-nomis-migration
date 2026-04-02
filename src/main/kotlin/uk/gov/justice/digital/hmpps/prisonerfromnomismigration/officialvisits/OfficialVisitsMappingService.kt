package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBodilessEntity
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.SuccessOrDuplicate
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrNullWhenNotFound
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitSuccessOrDuplicate
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

  suspend fun createVisitMapping(mapping: OfficialVisitMappingDto): SuccessOrDuplicate<OfficialVisitMappingDto> = api.prepare(api.createOfficialVisitMappingRequestConfig(mapping))
    .retrieve()
    .awaitSuccessOrDuplicate()

  suspend fun replaceMappingsByNomisId(mappings: OfficialVisitReplaceMappingDto) {
    // TODO: does not exist yet
    webClient.post().uri("/mapping/official-visits/replace-by-nomis-ids")
      .bodyValue(mappings)
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun getByVisitNomisIdOrNull(nomisVisitId: Long): OfficialVisitMappingDto? = api.prepare(
    api.getVisitMappingByNomisIdRequestConfig(
      nomisVisitId = nomisVisitId,
    ),
  )
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun hasVisitForNomisId(nomisVisitId: Long) = getByVisitNomisIdOrNull(nomisVisitId) != null

  suspend fun getByVisitNomisId(nomisVisitId: Long): OfficialVisitMappingDto = api.getVisitMappingByNomisId(
    nomisVisitId = nomisVisitId,
  ).awaitSingle()

  suspend fun deleteByVisitNomisId(nomisVisitId: Long) {
    api.deleteOfficialVisitMapping(nomisVisitId = nomisVisitId).awaitSingle()
  }

  suspend fun createVisitorMapping(mapping: OfficialVisitorMappingDto): SuccessOrDuplicate<OfficialVisitorMappingDto> = api.prepare(api.createVisitorMappingRequestConfig(mapping))
    .retrieve()
    .awaitSuccessOrDuplicate()

  suspend fun getByVisitorNomisIdOrNull(nomisVisitorId: Long): OfficialVisitorMappingDto? = api.prepare(
    api.getVisitorMappingByNomisIdRequestConfig(
      nomisVisitorId = nomisVisitorId,
    ),
  )
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun hasVisitorForNomisId(nomisVisitorId: Long) = getByVisitorNomisIdOrNull(nomisVisitorId) != null

  suspend fun getByVisitorNomisId(nomisVisitorId: Long): OfficialVisitorMappingDto = api.getVisitorMappingByNomisId(nomisVisitorId = nomisVisitorId)
    .awaitSingle()

  suspend fun deleteByVisitorNomisId(nomisVisitorId: Long) {
    api.deleteOfficialVisitorMapping(nomisVisitorId = nomisVisitorId).awaitSingle()
  }

  suspend fun getInternalLocationByNomisId(nomisLocationId: Long): LocationMappingDto = locationApi.getLocationMappingGivenNomisId(nomisLocationId = nomisLocationId).awaitSingle()
}

// TODO - does not exist yet
data class OfficialVisitReplaceMappingDto(
  val mappings: List<OfficialVisitMigrationMappingDto>,
)
