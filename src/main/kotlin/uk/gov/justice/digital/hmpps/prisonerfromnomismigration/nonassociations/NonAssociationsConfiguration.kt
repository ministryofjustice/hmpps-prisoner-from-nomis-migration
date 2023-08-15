package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nonassociations

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
class NonAssociationsConfiguration(
  @Value("\${api.base.url.non-associations}") val nonAssociationsApiBaseUri: String,
) {

  @Bean
  fun nonAssociationsApiHealthWebClient(): WebClient {
    return WebClient.builder()
      .baseUrl(nonAssociationsApiBaseUri)
      .build()
  }

  @Bean
  fun nonAssociationsApiWebClient(authorizedClientManager: ReactiveOAuth2AuthorizedClientManager, webClientBuilder: WebClient.Builder): WebClient {
    val oauth2Client = ServerOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager)
    oauth2Client.setDefaultClientRegistrationId("non-associations-api")

    return webClientBuilder
      .baseUrl(nonAssociationsApiBaseUri)
      .filter(oauth2Client)
      .build()
  }

  @Component("nonAssociationsApi")
  class NonAssociationsApiHealth
  constructor(@Qualifier("nonAssociationsApiHealthWebClient") webClient: WebClient) : HealthCheck(webClient)
}
