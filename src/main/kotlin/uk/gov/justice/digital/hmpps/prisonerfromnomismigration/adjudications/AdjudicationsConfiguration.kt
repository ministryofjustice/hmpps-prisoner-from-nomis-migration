package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications

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
class AdjudicationsConfiguration(
  @Value("\${api.base.url.adjudications}") val adjudicationsApiBaseUri: String,
) {

  @Bean
  fun adjudicationsApiHealthWebClient(): WebClient {
    return WebClient.builder()
      .baseUrl(adjudicationsApiBaseUri)
      .build()
  }

  @Bean
  fun adjudicationsApiWebClient(authorizedClientManager: ReactiveOAuth2AuthorizedClientManager, webClientBuilder: WebClient.Builder): WebClient {
    val oauth2Client = ServerOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager)
    oauth2Client.setDefaultClientRegistrationId("adjudications-api")

    return webClientBuilder
      .baseUrl(adjudicationsApiBaseUri)
      .filter(oauth2Client)
      .build()
  }

  @Component("adjudicationsApi")
  class AdjudicationsApiHealth
  constructor(@Qualifier("adjudicationsApiHealthWebClient") webClient: WebClient) : HealthCheck(webClient)
}
