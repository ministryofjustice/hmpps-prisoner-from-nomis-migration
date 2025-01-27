package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.locations

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
class LocationsConfiguration(
  @Value("\${api.base.url.locations}") val locationsApiBaseUri: String,
  @Value("\${api.health-timeout:2s}") val healthTimeout: Duration,
  @Value("\${api.timeout:90s}") val timeout: Duration,
) {

  @Bean
  fun locationsApiHealthWebClient(builder: WebClient.Builder): WebClient = builder.reactiveHealthWebClient(locationsApiBaseUri, healthTimeout)

  @Bean
  fun locationsApiWebClient(authorizedClientManager: ReactiveOAuth2AuthorizedClientManager, builder: WebClient.Builder): WebClient = builder.reactiveAuthorisedWebClient(authorizedClientManager, registrationId = "locations-api", url = locationsApiBaseUri, timeout)

  @Component("locationsApi")
  class LocationsApiHealth(@Qualifier("locationsApiHealthWebClient") webClient: WebClient) : ReactiveHealthPingCheck(webClient)
}
