package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.servers.Server
import io.swagger.v3.oas.models.tags.Tag
import org.springframework.boot.info.BuildProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfiguration(buildProperties: BuildProperties) {
  private val version: String = buildProperties.version!!

  @Bean
  fun customOpenAPI(): OpenAPI = OpenAPI()
    .servers(
      listOf(
        Server().url("https://prisoner-nomis-migration-dev.hmpps.service.justice.gov.uk").description("Development"),
        Server().url("https://prisoner-nomis-migration-preprod.hmpps.service.justice.gov.uk").description("PreProd"),
        Server().url("https://prisoner-nomis-migration.hmpps.service.justice.gov.uk").description("Prod"),
        Server().url("http://localhost:8080").description("Local"),
      ),
    )
    .info(
      Info().title("Prisoner from NOMIS Migration API")
        .version(version)
        .description(
          """API to launch migration of prisoner data from NOMIS to new services
          <br/> All hmpps-queue-resource endpoints require the role <b>QUEUE_ADMIN</b> 
          """,
        )
        .contact(Contact().name("HMPPS Digital Studio").email("feedback@digital.justice.gov.uk")),
    )
    .tags(
      listOf(
        Tag().name("hmpps-queue-resource-async")
          .description("""Endpoints that are to be used by administrators only for managing SQS queues. All endpoints require the <b>QUEUE_ADMIN</b> role further information can be found in the <a href="https://github.com/ministryofjustice/hmpps-spring-boot-sqs">hmpps-spring-boot-sqs</a> project"""),
      ),
    )
    .components(
      Components().addSecuritySchemes(
        "bearer-jwt",
        SecurityScheme()
          .type(SecurityScheme.Type.HTTP)
          .scheme("bearer")
          .bearerFormat("JWT")
          .`in`(SecurityScheme.In.HEADER)
          .name("Authorization").description("An HMPPS Auth access token."),
      ),
    )
    .addSecurityItem(SecurityRequirement().addList("bearer-jwt", listOf("read", "write")))
}
