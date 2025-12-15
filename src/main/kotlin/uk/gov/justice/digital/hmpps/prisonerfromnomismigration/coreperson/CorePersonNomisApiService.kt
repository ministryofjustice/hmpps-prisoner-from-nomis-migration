package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.api.CorePersonResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CorePerson

@Service
class CorePersonNomisApiService(@Qualifier("nomisApiWebClient") private val webClient: WebClient) {
  private val api = CorePersonResourceApi(webClient)

  suspend fun getCorePerson(nomisPrisonNumber: String): CorePerson = api
    .getOffender(prisonNumber = nomisPrisonNumber)
    .awaitSingle()

  suspend fun getOffenderReligions(nomisPrisonNumber: String) = api.getOffenderReligions(nomisPrisonNumber)
    .awaitSingle()
}
