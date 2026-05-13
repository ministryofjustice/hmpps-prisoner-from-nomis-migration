package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.court

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.hmpps.kotlin.auth.reactiveAuthorisedWebClient
import java.time.Duration

@Configuration
class CourtSchedulerConfiguration(
  @Value("\${api.base.url.movements-court}") val tapsUrl: String,
  @Value("\${api.health-timeout:2s}") val healthTimeout: Duration,
  @Value("\${api.movements-court-timeout:10s}") val dpsTimeout: Duration,
  @Value("\${api.movements-court-resync-timeout:10s}") val dpsResyncTimeout: Duration,
  @Value("\${api.movements-court-mapping-timeout:60s}") val mappingTimeout: Duration,
  @Value("\${api.base.url.mapping}") val mappingApiBaseUri: String,
) {

  @Bean
  fun courtSchedulerMappingApiWebClient(authorizedClientManager: ReactiveOAuth2AuthorizedClientManager, builder: WebClient.Builder): WebClient = builder.reactiveAuthorisedWebClient(authorizedClientManager, registrationId = "nomis-mapping-api", url = mappingApiBaseUri, mappingTimeout)

  @Bean
  fun courtSchedulerDpsApiWebClient(
    authorizedClientManager: ReactiveOAuth2AuthorizedClientManager,
    builder: WebClient.Builder,
  ): WebClient = builder.reactiveAuthorisedWebClient(authorizedClientManager, registrationId = "movements-court-api", url = tapsUrl, dpsTimeout)

  @Bean
  fun courtSchedulerDpsApiResyncWebClient(
    authorizedClientManager: ReactiveOAuth2AuthorizedClientManager,
    builder: WebClient.Builder,
  ): WebClient = builder.reactiveAuthorisedWebClient(authorizedClientManager, registrationId = "movements-court-api", url = tapsUrl, dpsResyncTimeout)
}
