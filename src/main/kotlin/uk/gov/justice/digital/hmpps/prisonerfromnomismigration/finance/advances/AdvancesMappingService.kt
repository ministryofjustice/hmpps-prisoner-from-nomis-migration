package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.advances

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrNullWhenNotFound
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.MigrationMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.api.AdvanceMappingResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.AdvanceMappingDto

@Service
class AdvancesMappingService(@Qualifier("mappingApiWebClient") webClient: WebClient) : MigrationMapping<AdvanceMappingDto>("/mapping/advances", webClient) {
  private val api = AdvanceMappingResourceApi(webClient)

  suspend fun getByNomisIdOrNull(nomisId: Long): AdvanceMappingDto? = api
    .prepare(api.getAdvanceMappingByNomisIdRequestConfig(nomisId))
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun getByNomisId(nomisId: Long): AdvanceMappingDto = api
    .getAdvanceMappingByNomisId(nomisId)
    .awaitSingle()
}
