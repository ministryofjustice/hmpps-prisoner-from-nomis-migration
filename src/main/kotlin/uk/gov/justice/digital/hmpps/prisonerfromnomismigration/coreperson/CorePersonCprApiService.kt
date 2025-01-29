package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.CreateResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrLogAndRethrowBadRequest

@Service
class CorePersonCprApiService(@Qualifier("corePersonApiWebClient") private val webClient: WebClient) {
  suspend fun migrateCorePerson(prisonNumber: String, corePerson: Prisoner): CreateResponse = webClient.put()
    .uri("/syscon-sync/{prisonNumber}", prisonNumber)
    .bodyValue(corePerson)
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()
}
