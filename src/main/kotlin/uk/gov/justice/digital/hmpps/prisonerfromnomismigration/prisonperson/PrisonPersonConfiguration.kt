package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.hmpps.kotlin.auth.reactiveAuthorisedWebClient
import uk.gov.justice.hmpps.kotlin.auth.reactiveHealthWebClient
import java.time.Duration

@Configuration
class PrisonPersonConfiguration(
  @Value("\${api.base.url.prison.person}") val apiBaseUri: String,
  @Value("\${api.health-timeout:2s}") val healthTimeout: Duration,
  @Value("\${api.timeout:90s}") val timeout: Duration,
) {

  @Bean
  fun prisonPersonApiHealthWebClient(builder: WebClient.Builder): WebClient = builder.reactiveHealthWebClient(apiBaseUri, healthTimeout)

  @Bean
  fun prisonPersonApiWebClient(
    authorizedClientManager: ReactiveOAuth2AuthorizedClientManager,
    builder: WebClient.Builder,
  ): WebClient = builder.reactiveAuthorisedWebClient(authorizedClientManager, registrationId = "prison-person-api", url = apiBaseUri, timeout)
}
