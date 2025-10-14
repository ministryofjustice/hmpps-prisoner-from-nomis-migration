package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.hmpps.kotlin.auth.reactiveAuthorisedWebClient
import java.time.Duration

@Configuration
class SentencingConfiguration(
  @Value("\${api.base.url.sentencing}") val sentencingApiBaseUri: String,
  @Value("\${api.timeout:90s}") val timeout: Duration,
) {

  @Bean
  fun sentencingApiWebClient(authorizedClientManager: ReactiveOAuth2AuthorizedClientManager, builder: WebClient.Builder): WebClient = builder.reactiveAuthorisedWebClient(authorizedClientManager, registrationId = "sentencing-api", url = sentencingApiBaseUri, timeout)
}
