package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.security.oauth2.client.AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientProviderBuilder
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientService
import org.springframework.security.oauth2.client.endpoint.WebClientReactiveClientCredentialsTokenResponseClient
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import uk.gov.justice.hmpps.kotlin.auth.reactiveAuthorisedWebClient
import uk.gov.justice.hmpps.kotlin.auth.reactiveHealthWebClient
import java.time.Duration

@Configuration
class WebClientConfiguration(
  @Value("\${api.base.url.nomis}") val nomisApiBaseUri: String,
  @Value("\${api.base.url.hmpps-auth}") val hmppsAuthApiBaseUri: String,
  @Value("\${api.base.url.mapping}") val mappingApiBaseUri: String,
  @Value("\${api.base.url.nomis-sync}") val nomisSyncApiBaseUri: String,
  @Value("\${api.health-timeout:2s}") val healthTimeout: Duration,
  @Value("\${api.timeout:90s}") val timeout: Duration,
) {

  @Bean
  fun nomisApiHealthWebClient(builder: WebClient.Builder): WebClient = builder.reactiveHealthWebClient(nomisApiBaseUri, healthTimeout)

  @Bean
  fun mappingApiHealthWebClient(builder: WebClient.Builder): WebClient = builder.reactiveHealthWebClient(mappingApiBaseUri, healthTimeout)

  @Bean
  fun hmppsAuthApiHealthWebClient(builder: WebClient.Builder): WebClient = builder.reactiveHealthWebClient(hmppsAuthApiBaseUri, healthTimeout)

  @Bean
  fun nomisSyncApiHealthWebClient(builder: WebClient.Builder): WebClient = builder.reactiveHealthWebClient(nomisSyncApiBaseUri, healthTimeout)

  @Bean
  fun nomisApiWebClient(authorizedClientManager: ReactiveOAuth2AuthorizedClientManager, builder: WebClient.Builder): WebClient =
    builder.reactiveAuthorisedWebClient(authorizedClientManager, registrationId = "nomis-api", url = nomisApiBaseUri, timeout)

  @Bean
  fun mappingApiWebClient(authorizedClientManager: ReactiveOAuth2AuthorizedClientManager, builder: WebClient.Builder): WebClient =
    builder.reactiveAuthorisedWebClient(authorizedClientManager, registrationId = "nomis-mapping-api", url = mappingApiBaseUri, timeout)

  @Bean
  fun nomisSyncApiWebClient(authorizedClientManager: ReactiveOAuth2AuthorizedClientManager, builder: WebClient.Builder): WebClient =
    builder.reactiveAuthorisedWebClient(authorizedClientManager, registrationId = "nomis-sync-api", url = nomisSyncApiBaseUri, timeout)

  @Bean
  fun reactiveOAuth2AuthorizedClientManagerWithTimeout(
    reactiveClientRegistrationRepository: ReactiveClientRegistrationRepository,
    reactiveOAuth2AuthorizedClientService: ReactiveOAuth2AuthorizedClientService,
  ): ReactiveOAuth2AuthorizedClientManager = AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager(
    reactiveClientRegistrationRepository,
    reactiveOAuth2AuthorizedClientService,
  ).apply {
    val accessTokenResponseClient =
      WebClientReactiveClientCredentialsTokenResponseClient().apply {
        this.setWebClient(
          WebClient.builder().clientConnector(
            ReactorClientHttpConnector(
              HttpClient.create().responseTimeout(timeout),
            ),
          ).build(),
        )
      }

    setAuthorizedClientProvider(
      ReactiveOAuth2AuthorizedClientProviderBuilder
        .builder()
        .clientCredentials { it.accessTokenResponseClient(accessTokenResponseClient).build() }
        .build(),
    )
  }
}
