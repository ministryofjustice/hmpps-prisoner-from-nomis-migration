package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import uk.gov.justice.hmpps.kotlin.auth.dsl.ResourceServerConfigurationCustomizer

@Configuration
class ReactiveResourceServerConfiguration {

  @Bean
  fun resourceServerCustomizer() = ResourceServerConfigurationCustomizer.build {
    unauthorizedRequestPaths {
      addPaths = setOf("/queue-admin/retry-all-dlqs")
    }
  }
}
