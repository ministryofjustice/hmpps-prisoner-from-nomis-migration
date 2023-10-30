package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications

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
class AdjudicationsConfiguration(
  @Value("\${api.base.url.adjudications}") val adjudicationsApiBaseUri: String,
  @Value("\${api.health-timeout:2s}") val healthTimeout: Duration,
  @Value("\${api.timeout:90s}") val timeout: Duration,
) {

  @Bean
  fun adjudicationsApiHealthWebClient(builder: WebClient.Builder): WebClient = builder.healthWebClient(adjudicationsApiBaseUri, healthTimeout)

  @Bean
  fun adjudicationsApiWebClient(authorizedClientManager: ReactiveOAuth2AuthorizedClientManager, builder: WebClient.Builder): WebClient =
    builder.authorisedWebClient(authorizedClientManager, registrationId = "adjudications-api", url = adjudicationsApiBaseUri, timeout)

  @Component("adjudicationsApi")
  class AdjudicationsApiHealth(@Qualifier("adjudicationsApiHealthWebClient") webClient: WebClient) : HealthCheck(webClient)
}
