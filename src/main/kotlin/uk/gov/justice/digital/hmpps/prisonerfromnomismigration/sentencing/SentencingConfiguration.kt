package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.web.reactive.function.client.ServerOAuth2AuthorizedClientExchangeFilterFunction
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.health.HealthCheck

@Configuration
class SentencingConfiguration(
  @Value("\${api.base.url.sentencing}") val sentencingApiBaseUri: String,
) {

  @Bean
  fun sentencingApiHealthWebClient(): WebClient {
    return WebClient.builder()
      .baseUrl(sentencingApiBaseUri)
      .build()
  }

  @Bean
  fun sentencingApiWebClient(authorizedClientManager: ReactiveOAuth2AuthorizedClientManager): WebClient {
    val oauth2Client = ServerOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager)
    oauth2Client.setDefaultClientRegistrationId("sentencing-api")

    return WebClient.builder()
      .baseUrl(sentencingApiBaseUri)
      .filter(oauth2Client)
      .build()
  }

  @Component("sentencingApi")
  class SentencingApiHealth
  constructor(@Qualifier("sentencingApiHealthWebClient") webClient: WebClient) : HealthCheck(webClient)
}