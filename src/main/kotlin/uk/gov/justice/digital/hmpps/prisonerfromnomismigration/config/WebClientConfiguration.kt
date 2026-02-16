package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientProvider
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.hmpps.kotlin.auth.reactiveAuthorisedWebClient
import uk.gov.justice.hmpps.kotlin.auth.reactiveHealthWebClient
import uk.gov.justice.hmpps.kotlin.auth.reactiveOAuth2AuthorizedClientProvider
import java.time.Duration

@Configuration
class WebClientConfiguration(
  @Value("\${api.base.url.nomis}") val nomisApiBaseUri: String,
  @Value("\${api.base.url.hmpps-auth}") val hmppsAuthApiBaseUri: String,
  @Value("\${api.base.url.mapping}") val mappingApiBaseUri: String,
  @Value("\${api.base.url.nomis-sync}") val nomisSyncApiBaseUri: String,
  @Value("\${api.health-timeout:2s}") val healthTimeout: Duration,
  @Value("\${api.timeout:60s}") val timeout: Duration,
  @Value("\${api.auth-timeout:10s}") val authTimeout: Duration,
  @Value("\${api.mapping-timeout:10s}") val mappingTimeout: Duration,
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
  fun nomisApiWebClient(authorizedClientManager: ReactiveOAuth2AuthorizedClientManager, builder: WebClient.Builder): WebClient = builder.reactiveAuthorisedWebClient(authorizedClientManager, registrationId = "nomis-api", url = nomisApiBaseUri, timeout)

  @Bean
  fun mappingApiWebClient(authorizedClientManager: ReactiveOAuth2AuthorizedClientManager, builder: WebClient.Builder): WebClient = builder.reactiveAuthorisedWebClient(authorizedClientManager, registrationId = "nomis-mapping-api", url = mappingApiBaseUri, mappingTimeout)

  @Bean
  fun nomisSyncApiWebClient(authorizedClientManager: ReactiveOAuth2AuthorizedClientManager, builder: WebClient.Builder): WebClient = builder.reactiveAuthorisedWebClient(authorizedClientManager, registrationId = "nomis-sync-api", url = nomisSyncApiBaseUri, timeout)

  @Bean
  fun reactiveOAuth2AuthorizedClientProvider(builder: WebClient.Builder): ReactiveOAuth2AuthorizedClientProvider = builder.reactiveOAuth2AuthorizedClientProvider(authTimeout)
}
