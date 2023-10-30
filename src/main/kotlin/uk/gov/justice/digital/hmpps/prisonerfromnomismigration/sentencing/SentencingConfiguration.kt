package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing

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
class SentencingConfiguration(
  @Value("\${api.base.url.sentencing}") val sentencingApiBaseUri: String,
  @Value("\${api.health-timeout:2s}") val healthTimeout: Duration,
  @Value("\${api.timeout:90s}") val timeout: Duration,
) {

  @Bean
  fun sentencingApiHealthWebClient(builder: WebClient.Builder): WebClient = builder.healthWebClient(sentencingApiBaseUri, healthTimeout)

  @Bean
  fun sentencingApiWebClient(authorizedClientManager: ReactiveOAuth2AuthorizedClientManager, builder: WebClient.Builder): WebClient =
    builder.authorisedWebClient(authorizedClientManager, registrationId = "sentencing-api", url = sentencingApiBaseUri, timeout)

  @Component("sentencingApi")
  class SentencingApiHealth(@Qualifier("sentencingApiHealthWebClient") webClient: WebClient) : HealthCheck(webClient)
}
