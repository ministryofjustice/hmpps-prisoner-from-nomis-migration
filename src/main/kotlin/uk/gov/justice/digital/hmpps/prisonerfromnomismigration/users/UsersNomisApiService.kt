package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.users

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.api.UserResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.UserDetails

@Service
class UsersNomisApiService(@Qualifier("nomisApiWebClient") webClient: WebClient) {
  private val api = UserResourceApi(webClient)

  suspend fun getUserDetails(userId: Long): UserDetails = api
    .prepare(api.getUserRequestConfig(userId))
    .retrieve()
    .awaitBody()
}
