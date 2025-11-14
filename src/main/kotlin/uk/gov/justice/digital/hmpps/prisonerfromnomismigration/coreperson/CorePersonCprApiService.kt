package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonSexualOrientation
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonSexualOrientationResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrLogAndRethrowBadRequest
import java.util.UUID

@Service
class CorePersonCprApiService(@Qualifier("corePersonApiWebClient") private val webClient: WebClient) {
  suspend fun migrateCorePerson(prisonNumber: String, corePerson: Prisoner): String = webClient
    .put()
    .uri("/syscon-sync/{prisonNumber}", prisonNumber)
    .bodyValue(corePerson)
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun syncCreateSexualOrientation(request: PrisonSexualOrientation): PrisonSexualOrientationResponse = webClient
    .post()
    .uri("/syscon-sync/sexual-orientation")
    .bodyValue(request)
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun syncUpdateSexualOrientation(cprSexualOrientationId: UUID, request: PrisonSexualOrientation): String = webClient
    .post()
    .uri("//syscon-sync/sexual-orientation/{cprSexualOrientationId}", cprSexualOrientationId)
    .bodyValue(request)
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()
}
