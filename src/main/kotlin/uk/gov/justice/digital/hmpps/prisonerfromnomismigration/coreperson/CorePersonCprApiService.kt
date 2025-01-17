package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrLogAndRethrowBadRequest

@Service
class CorePersonCprApiService(@Qualifier("corePersonApiWebClient") private val webClient: WebClient) {
  suspend fun migrateCore(core: MigrateCorePersonRequest): MigrateCorePersonResponse = webClient.post()
    .uri("/syscon-sync")
    .bodyValue(core)
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()
}
