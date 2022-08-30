package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incentives

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
class IncentivesConfiguration(
  @Value("\${api.base.url.incentives}") val incentivesApiBaseUri: String,
) {

  @Bean
  fun incentivesApiHealthWebClient(): WebClient {
    return WebClient.builder()
      .baseUrl(incentivesApiBaseUri)
      .build()
  }

  @Bean
  fun incentivesApiWebClient(authorizedClientManager: ReactiveOAuth2AuthorizedClientManager): WebClient {
    val oauth2Client = ServerOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager)
    oauth2Client.setDefaultClientRegistrationId("incentives-api")

    return WebClient.builder()
      .baseUrl(incentivesApiBaseUri)
      .filter(oauth2Client)
      .build()
  }

  @Component("incentivesApi")
  class VisitsApiHealth
  constructor(@Qualifier("incentivesApiHealthWebClient") webClient: WebClient) : HealthCheck(webClient)
}
