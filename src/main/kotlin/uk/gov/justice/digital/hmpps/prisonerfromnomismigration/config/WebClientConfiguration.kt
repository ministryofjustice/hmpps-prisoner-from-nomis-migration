package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.client.AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientProviderBuilder
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientService
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository
import org.springframework.security.oauth2.client.web.reactive.function.client.ServerOAuth2AuthorizedClientExchangeFilterFunction
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.health.HealthCheck

@Configuration
class WebClientConfiguration(
  @Value("\${api.base.url.nomis}") val nomisApiBaseUri: String,
  @Value("\${api.base.url.oauth}") val oauthApiBaseUri: String,
  @Value("\${api.base.url.mapping}") val mappingApiBaseUri: String,
) {

  @Bean
  fun nomisApiHealthWebClient(): WebClient {
    return WebClient.builder()
      .baseUrl(nomisApiBaseUri)
      .build()
  }

  @Bean
  fun oauthApiHealthWebClient(): WebClient {
    return WebClient.builder()
      .baseUrl(oauthApiBaseUri)
      .build()
  }

  @Bean
  fun nomisApiWebClient(authorizedClientManager: ReactiveOAuth2AuthorizedClientManager): WebClient {
    val oauth2Client = ServerOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager)

    oauth2Client.setDefaultClientRegistrationId("nomis-api")

    return WebClient.builder()
      .baseUrl(nomisApiBaseUri)
      .filter(oauth2Client)
      .build()
  }

  @Bean
  fun authorizedClientManager(
    clientRegistrationRepository: ReactiveClientRegistrationRepository,
    oAuth2AuthorizedClientService: ReactiveOAuth2AuthorizedClientService
  ): ReactiveOAuth2AuthorizedClientManager? {
    val authorizedClientProvider = ReactiveOAuth2AuthorizedClientProviderBuilder.builder().clientCredentials().build()
    val authorizedClientManager =
      AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager(clientRegistrationRepository, oAuth2AuthorizedClientService)
    authorizedClientManager.setAuthorizedClientProvider(authorizedClientProvider)
    return authorizedClientManager
  }

  @Bean
  fun mappingApiWebClient(authorizedClientManager: ReactiveOAuth2AuthorizedClientManager): WebClient {
    val oauth2Client = ServerOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager)
    oauth2Client.setDefaultClientRegistrationId("visit-mapping-api")

    return WebClient.builder()
      .baseUrl(mappingApiBaseUri)
      .filter(oauth2Client)
      .build()
  }

  @Bean
  fun mappingApiHealthWebClient(): WebClient {
    return WebClient.builder()
      .baseUrl(mappingApiBaseUri)
      .build()
  }

  @Component("visitMappingApi")
  class MappingApiHealth
  constructor(@Qualifier("mappingApiHealthWebClient") webClient: WebClient) : HealthCheck(webClient)
}
