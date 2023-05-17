package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities

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
class ActivitiesConfiguration(
  @Value("\${api.base.url.activities}") val activitiesApiBaseUri: String,
) {

  @Bean
  fun activitiesApiHealthWebClient(): WebClient {
    return WebClient.builder()
      .baseUrl(activitiesApiBaseUri)
      .build()
  }

  @Bean
  fun activitiesApiWebClient(authorizedClientManager: ReactiveOAuth2AuthorizedClientManager, webClientBuilder: WebClient.Builder): WebClient {
    val oauth2Client = ServerOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager)
    oauth2Client.setDefaultClientRegistrationId("activities-api")

    return webClientBuilder
      .baseUrl(activitiesApiBaseUri)
      .filter(oauth2Client)
      .build()
  }

  @Component("activitiesApi")
  class ActivitiesApiHealth
  constructor(@Qualifier("activitiesApiHealthWebClient") webClient: WebClient) : HealthCheck(webClient)
}
