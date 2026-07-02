package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.property

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.property.api.SyncWithNOMISApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.property.model.SyncPropertyContainerRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.property.model.SyncPropertyContainerResponse

@Service
class PropertyDpsApiService(@Qualifier("propertyApiWebClient") private val webClient: WebClient) {
  private val api = SyncWithNOMISApi(webClient)

  suspend fun migrate(request: SyncPropertyContainerRequest): SyncPropertyContainerResponse = api
    .migrate(request).awaitSingle()

  suspend fun upsert(request: SyncPropertyContainerRequest): SyncPropertyContainerResponse = api
    .upsert(request).awaitSingle()
}
