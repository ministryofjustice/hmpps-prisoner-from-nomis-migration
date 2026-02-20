package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csra

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.api.CsraResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerCsrasResponse

@Service
class CsraNomisApiService(@Qualifier("nomisApiWebClient") webClient: WebClient) {
  private val csraNomisApi = CsraResourceApi(webClient)

  suspend fun getCsras(offenderNo: String): PrisonerCsrasResponse = csraNomisApi
    .getCsrasForPrisoner(offenderNo).awaitSingle()
}
