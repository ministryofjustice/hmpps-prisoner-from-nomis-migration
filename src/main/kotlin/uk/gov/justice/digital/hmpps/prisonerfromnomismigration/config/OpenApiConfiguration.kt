package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.servers.Server
import org.springframework.boot.info.BuildProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfiguration(buildProperties: BuildProperties) {
  private val version: String = buildProperties.version

  @Bean
  fun customOpenAPI(): OpenAPI = OpenAPI()
    .servers(
      listOf(
        Server().url("https://prisoner-nomis-migration.hmpps.service.justice.gov.uk").description("Prod"),
        Server().url("https://prisoner-nomis-migration-preprod.hmpps.service.justice.gov.uk").description("PreProd"),
        Server().url("https://prisoner-nomis-migration-dev.hmpps.service.justice.gov.uk").description("Development"),
        Server().url("http://localhost:8080").description("Local"),
      )
    )
    .info(
      Info().title("Prisoner from NOMIS Migration API")
        .version(version)
        .description("API to launch migration of prisoner data from NOMIS to new services")
        .contact(Contact().name("HMPPS Digital Studio").email("feedback@digital.justice.gov.uk"))
    )
}
