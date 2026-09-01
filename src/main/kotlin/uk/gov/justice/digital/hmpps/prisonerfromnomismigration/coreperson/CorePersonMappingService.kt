package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrNullWhenNotFound
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.MigrationMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.api.CorePersonMappingResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonMappingsDto

@Service
class CorePersonMappingService(@Qualifier("mappingApiWebClient") webClient: WebClient) : MigrationMapping<CorePersonMappingsDto>("/mapping/core-person", webClient) {
  private val api = CorePersonMappingResourceApi(webClient)

  suspend fun getCorePersonByPrisonNumberOrNull(prisonNumber: String): CorePersonMappingDto? = api
    .prepare(api.getPersonMappingByNomisPrisonNumberRequestConfig(prisonNumber))
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun replaceMappings(mappings: CorePersonMappingsDto) {
    api.replaceCorePersonMappings(mappings).awaitSingle()
  }
}
