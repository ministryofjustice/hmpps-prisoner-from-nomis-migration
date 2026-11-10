package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.api.CorePersonMergeMappingResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonAddressContactMappingsByNomisIdsRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonAddressContactMappingsResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonAddressContactMergeMappingsDto

@Service
class CorePersonMappingService(@Qualifier("mappingApiWebClient") webClient: WebClient) {
  private val mergeApi = CorePersonMergeMappingResourceApi(webClient)

  suspend fun getMappingsFromNomisIds(request: CorePersonAddressContactMappingsByNomisIdsRequest): CorePersonAddressContactMappingsResult = mergeApi
    .prepare(mergeApi.getMappingsFromNomisIdsRequestConfig(request))
    .retrieve()
    .awaitBody()

  suspend fun merge(mappings: CorePersonAddressContactMergeMappingsDto) {
    mergeApi.merge(mappings).awaitSingle()
  }
}
