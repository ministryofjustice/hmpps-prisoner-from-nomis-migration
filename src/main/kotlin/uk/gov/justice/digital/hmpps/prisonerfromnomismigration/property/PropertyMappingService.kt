package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.property

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitSingleOrNullForNotFound
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.MigrationMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.api.PropertyContainerMappingResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PropertyContainerMappingDto

@Service
class PropertyMappingService(
  @Qualifier("mappingApiWebClient") webClient: WebClient,
) : MigrationMapping<PropertyContainerMappingDto>(domainUrl = "/mapping/property", webClient) {
  private val api = PropertyContainerMappingResourceApi(webClient)

  suspend fun getMappingByNomisId(nomisId: Long) = api
    .getPropertyContainerMappingByNomisId(nomisId)
    .awaitSingleOrNullForNotFound()

  suspend fun deleteMapping(nomisId: Long) = api
    .deletePropertyContainerMappingByNomisId(nomisId).awaitSingle()

  suspend fun getLatestMigratedPropertyContainerMapping() = api
    .getLatestMigratedPropertyContainerMapping().awaitSingle()

  override suspend fun getMigrationCount(migrationId: String) = api
    .getPropertyContainerMappingsByMigrationIdCount(migrationId).awaitSingle()
  // ^ not using superclass for this as the endpoint is a pure count
}
