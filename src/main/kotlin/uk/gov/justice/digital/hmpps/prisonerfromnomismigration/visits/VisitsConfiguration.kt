package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visits

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.web.reactive.function.client.ServletOAuth2AuthorizedClientExchangeFilterFunction
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.health.HealthCheck

@Configuration
class VisitsConfiguration(
  @Value("\${api.base.url.visits}") val visitsApiBaseUri: String,
  @Value("\${api.base.url.visit.mapping}") val visitMappingApiBaseUri: String,
) {

  @Bean
  fun visitsApiHealthWebClient(): WebClient {
    return WebClient.builder()
      .baseUrl(visitsApiBaseUri)
      .build()
  }

  @Bean
  fun visitMappingApiHealthWebClient(): WebClient {
    return WebClient.builder()
      .baseUrl(visitMappingApiBaseUri)
      .build()
  }

  @Bean
  fun visitsApiWebClient(authorizedClientManager: OAuth2AuthorizedClientManager): WebClient {
    val oauth2Client = ServletOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager)
    oauth2Client.setDefaultClientRegistrationId("visits-api")

    return WebClient.builder()
      .baseUrl(visitsApiBaseUri)
      .apply(oauth2Client.oauth2Configuration())
      .build()
  }

  @Bean
  fun visitMappingApiWebClient(authorizedClientManager: OAuth2AuthorizedClientManager): WebClient {
    val oauth2Client = ServletOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager)
    oauth2Client.setDefaultClientRegistrationId("visit-mapping-api")

    return WebClient.builder()
      .baseUrl(visitMappingApiBaseUri)
      .apply(oauth2Client.oauth2Configuration())
      .build()
  }

  @Component("visitsApi")
  class VisitsApiHealth
  constructor(@Qualifier("visitsApiHealthWebClient") webClient: WebClient) : HealthCheck(webClient)

  @Component("visitMappingApi")
  class VisitMappingApiHealth
  constructor(@Qualifier("visitMappingApiHealthWebClient") webClient: WebClient) : HealthCheck(webClient)
}
