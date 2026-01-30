package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csra

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient

@Service
class CsraNomisApiService(@Qualifier("nomisApiWebClient") webClient: WebClient) {
  suspend fun todo() {}
}
