package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.prisonerlocation

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.hmpps.kotlin.auth.reactiveAuthorisedWebClient
import uk.gov.justice.hmpps.kotlin.auth.reactiveHealthWebClient
import uk.gov.justice.hmpps.kotlin.health.ReactiveHealthPingCheck
import java.time.Duration

@Configuration
class PrisonerLocationConfiguration(
  @Value("\${api.base.url.prisoner-location}") val prisonerLocationUrl: String,
  @Value("\${api.health-timeout:2s}") val healthTimeout: Duration,
  @Value("\${api.prisoner-location-timeout:10s}") val dpsTimeout: Duration,
) {

  @Bean
  fun prisonerLocationDpsApiWebClient(
    authorizedClientManager: ReactiveOAuth2AuthorizedClientManager,
    builder: WebClient.Builder,
  ): WebClient = builder.reactiveAuthorisedWebClient(authorizedClientManager, registrationId = "prisoner-location-api", url = prisonerLocationUrl, dpsTimeout)

  @Bean
  fun prisonerLocationApiHealthWebClient(builder: WebClient.Builder): WebClient = builder.reactiveHealthWebClient(prisonerLocationUrl, healthTimeout)

  @Component("prisonerLocationApi")
  class PrisonerLocationApiHealth(@Qualifier("prisonerLocationApiHealthWebClient") webClient: WebClient) : ReactiveHealthPingCheck(webClient)
}
