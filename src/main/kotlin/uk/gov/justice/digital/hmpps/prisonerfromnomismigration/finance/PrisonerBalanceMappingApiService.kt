package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrNullWhenNotFound
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.MigrationMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.api.PrisonerBalanceMappingResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PrisonerBalanceMappingDto

@Service
class PrisonerBalanceMappingApiService(@Qualifier("mappingApiWebClient") webClient: WebClient) : MigrationMapping<PrisonerBalanceMappingDto>(domainUrl = "/mapping/prisoner-balance", webClient) {
  private val api = PrisonerBalanceMappingResourceApi(webClient)

  suspend fun getByNomisIdOrNull(nomisId: Long): PrisonerBalanceMappingDto? = api
    .prepare(api.getPrisonerMappingByNomisIdRequestConfig(nomisId))
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun getByNomisId(nomisId: Long): PrisonerBalanceMappingDto = api
    .getPrisonerMappingByNomisId(nomisId)
    .awaitSingle()
}
