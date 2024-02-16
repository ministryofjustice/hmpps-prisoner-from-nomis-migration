package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.authorisedWebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.healthWebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.health.HealthCheck
import java.time.Duration

@Configuration
class AlertsConfiguration(
  @Value("\${api.base.url.alerts}") val apiBaseUri: String,
  @Value("\${api.health-timeout:2s}") val healthTimeout: Duration,
  @Value("\${api.timeout:90s}") val timeout: Duration,
) {

  @Bean
  fun alertsApiHealthWebClient(builder: WebClient.Builder): WebClient =
    builder.healthWebClient(apiBaseUri, healthTimeout)

  @Bean
  fun alertsApiWebClient(
    authorizedClientManager: ReactiveOAuth2AuthorizedClientManager,
    builder: WebClient.Builder,
  ): WebClient =
    builder.authorisedWebClient(authorizedClientManager, registrationId = "alerts-api", url = apiBaseUri, timeout)

  @Component("alertsApi")
  class AlertsApiHealth(@Qualifier("alertsApiHealthWebClient") webClient: WebClient) : HealthCheck(webClient)
}
