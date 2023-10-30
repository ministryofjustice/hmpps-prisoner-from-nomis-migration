package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.config.web.server.invoke
import org.springframework.security.web.server.SecurityWebFilterChain

@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity(useAuthorizationManager = false)
@EnableR2dbcRepositories
class ReactiveResourceServerConfiguration {

  @Bean
  fun springSecurityFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain =
    http {
      // Can't have CSRF protection as requires session
      csrf { disable() }
      authorizeExchange {
        listOf(
          "/webjars/**", "/favicon.ico", "/csrf",
          "/health/**", "/info", "/h2-console/**",
          "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html",
          "/queue-admin/retry-all-dlqs",
        ).forEach { authorize(it, permitAll) }
        authorize(anyExchange, authenticated)
      }
      oauth2ResourceServer { jwt { jwtAuthenticationConverter = ReactiveAuthAwareTokenConverter() } }
    }
}
