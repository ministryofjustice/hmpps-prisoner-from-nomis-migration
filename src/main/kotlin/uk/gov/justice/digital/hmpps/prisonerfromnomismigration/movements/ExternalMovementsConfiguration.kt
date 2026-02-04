package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.hmpps.kotlin.auth.reactiveAuthorisedWebClient
import uk.gov.justice.hmpps.kotlin.auth.reactiveHealthWebClient
import uk.gov.justice.hmpps.kotlin.health.ReactiveHealthPingCheck
import java.time.Duration

@Configuration
class ExternalMovementsConfiguration(
  @Value("\${api.base.url.ext.movements}") val apiBaseUri: String,
  @Value("\${api.health-timeout:2s}") val healthTimeout: Duration,
  @Value("\${api.ext-movements-timeout:10s}") val timeout: Duration,
  @Value("\${api.ext-movements-mapping-timeout:60s}") val mappingTimeout: Duration,
  @Value("\${api.base.url.mapping}") val mappingApiBaseUri: String,
) {

  @Bean
  fun extMovementsDpsApiWebClient(
    authorizedClientManager: ReactiveOAuth2AuthorizedClientManager,
    builder: WebClient.Builder,
  ): WebClient = builder.reactiveAuthorisedWebClient(authorizedClientManager, registrationId = "movements-api", url = apiBaseUri, timeout)

  @Bean
  fun extMovementsApiHealthWebClient(builder: WebClient.Builder): WebClient = builder.reactiveHealthWebClient(apiBaseUri, healthTimeout)

  @Bean
  fun extMovementsMappingApiWebClient(authorizedClientManager: ReactiveOAuth2AuthorizedClientManager, builder: WebClient.Builder): WebClient = builder.reactiveAuthorisedWebClient(authorizedClientManager, registrationId = "nomis-mapping-api", url = mappingApiBaseUri, mappingTimeout)

  @Component("extMovementsApi")
  class MovementsApiHealth(@Qualifier("extMovementsApiHealthWebClient") webClient: WebClient) : ReactiveHealthPingCheck(webClient)
}
