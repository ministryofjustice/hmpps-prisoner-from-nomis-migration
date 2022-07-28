package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.media.DateTimeSchema
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.media.StringSchema
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.servers.Server
import io.swagger.v3.oas.models.tags.Tag
import org.springdoc.core.customizers.OpenApiCustomiser
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
        Server().url("https://prisoner-nomis-migration-dev.hmpps.service.justice.gov.uk").description("Development"),
        Server().url("https://prisoner-nomis-migration-preprod.hmpps.service.justice.gov.uk").description("PreProd"),
        Server().url("https://prisoner-nomis-migration.hmpps.service.justice.gov.uk").description("Prod"),
        Server().url("http://localhost:8080").description("Local"),
      )
    )
    .info(
      Info().title("Prisoner from NOMIS Migration API")
        .version(version)
        .description("API to launch migration of prisoner data from NOMIS to new services<br/> All hmpps-queue-resource endpoints require the role <b>QUEUE_ADMIN</b> ")
        .contact(Contact().name("HMPPS Digital Studio").email("feedback@digital.justice.gov.uk"))
    )
    .tags(
      listOf(
        Tag().name("hmpps-queue-resource")
          .description("""DO NOT USE - These synchronous endpoints will not work. See the asynchronous ones below"""),
        Tag().name("hmpps-queue-resource-async")
          .description("""Endpoints that are to be used by administrators only for managing SQS queues. All endpoints require the <b>QUEUE_ADMIN</b> role further information can be found in the <a href="https://github.com/ministryofjustice/hmpps-spring-boot-sqs">hmpps-spring-boot-sqs</a> project"""),
      )
    )
    .components(
      Components().addSecuritySchemes(
        "bearer-jwt",
        SecurityScheme()
          .type(SecurityScheme.Type.HTTP)
          .scheme("bearer")
          .bearerFormat("JWT")
          .`in`(SecurityScheme.In.HEADER)
          .name("Authorization")
      )
    )
    .addSecurityItem(SecurityRequirement().addList("bearer-jwt", listOf("read", "write")))

  @Bean
  fun openAPICustomiser(): OpenApiCustomiser = OpenApiCustomiser {
    it.components.schemas.forEach { (_, schema: Schema<*>) ->
      val properties = schema.properties ?: mutableMapOf()
      for (propertyName in properties.keys) {
        val propertySchema = properties[propertyName]!!
        if (propertySchema is DateTimeSchema) {
          properties.replace(
            propertyName,
            StringSchema()
              .example("2021-07-05T10:35:17")
              .pattern("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}$")
              .description(propertySchema.description)
              .required(propertySchema.required)
          )
        }
      }
    }
  }
}
