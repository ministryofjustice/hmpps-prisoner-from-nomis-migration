package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrNullWhenNotFound
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.MigrationMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.api.PrisonBalanceMappingResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PrisonBalanceMappingDto

@Service
class PrisonBalanceMappingApiService(@Qualifier("mappingApiWebClient") webClient: WebClient) : MigrationMapping<PrisonBalanceMappingDto>(domainUrl = "/mapping/prison-balance", webClient) {
  private val api = PrisonBalanceMappingResourceApi(webClient)

  suspend fun getByNomisIdOrNull(nomisId: String): PrisonBalanceMappingDto? = api
    .prepare(api.getPrisonMappingByNomisIdRequestConfig(nomisId))
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun getByNomisId(nomisId: String): PrisonBalanceMappingDto = api
    .getPrisonMappingByNomisId(nomisId)
    .awaitSingle()
}
