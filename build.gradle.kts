import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import org.jlleitschuh.gradle.ktlint.tasks.KtLintCheckTask
import org.jlleitschuh.gradle.ktlint.tasks.KtLintFormatTask
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "7.1.1"
  kotlin("plugin.spring") version "2.1.10"
  id("org.openapi.generator") version "7.11.0"
}

configurations {
  implementation { exclude(module = "spring-boot-starter-web") }
  implementation { exclude(module = "spring-boot-starter-tomcat") }
  testImplementation { exclude(group = "org.junit.vintage") }
}

dependencies {
  implementation("uk.gov.justice.service.hmpps:hmpps-kotlin-spring-boot-starter:1.2.0")
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("uk.gov.justice.service.hmpps:hmpps-sqs-spring-boot-starter:5.3.0")
  implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")

  implementation("org.springdoc:springdoc-openapi-starter-webflux-ui:2.8.4")

  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk9")

  runtimeOnly("org.postgresql:r2dbc-postgresql:1.0.7.RELEASE")
  runtimeOnly("org.springframework.boot:spring-boot-starter-jdbc")
  runtimeOnly("org.postgresql:postgresql:42.7.5")
  runtimeOnly("org.flywaydb:flyway-database-postgresql")
  // Leaving at 1.43.0 to match the version used in App Insights https://github.com/microsoft/ApplicationInsights-Java/blob/3.6.2/dependencyManagement/build.gradle.kts#L14
  implementation("io.opentelemetry:opentelemetry-extension-kotlin:1.46.0")
  // Leaving at 2.9.0 to match the version used in App Insights https://github.com/microsoft/ApplicationInsights-Java/blob/3.6.2/dependencyManagement/build.gradle.kts#L16
  implementation("io.opentelemetry.instrumentation:opentelemetry-instrumentation-annotations:2.12.0")

  testImplementation("uk.gov.justice.service.hmpps:hmpps-kotlin-spring-boot-starter-test:1.2.0")
  testImplementation("io.swagger.parser.v3:swagger-parser:2.1.25") {
    exclude(group = "io.swagger.core.v3")
  }
  testImplementation("io.swagger.core.v3:swagger-core-jakarta:2.2.28")

  testImplementation("org.wiremock:wiremock-standalone:3.11.0")
  testImplementation("org.testcontainers:localstack:1.20.4")
  testImplementation("com.amazonaws:aws-java-sdk-core:1.12.780")
  testImplementation("org.awaitility:awaitility-kotlin:4.2.2")
  testImplementation("org.testcontainers:postgresql:1.20.4")
  testImplementation("io.mockk:mockk:1.13.16")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
}

kotlin {
  jvmToolchain(21)
}

data class ModelConfiguration(val name: String, val input: String, val output: String, val packageName: String)

val models = listOf(
  // https://activities-api-dev.prison.service.justice.gov.uk/v3/api-docs
  ModelConfiguration(
    name = "buildActivityApiModel",
    input = "activities-api-docs.json",
    output = "activities",
    packageName = "activities",
  ),
  // https://alerts-api-dev.hmpps.service.justice.gov.uk/v3/api-docs
  ModelConfiguration(
    name = "buildAlertsApiModel",
    input = "alerts-api-docs.json",
    output = "alerts",
    packageName = "alerts",
  ),
  // https://casenotes-api-dev.prison.service.justice.gov.uk/v3/api-docs
  ModelConfiguration(
    name = "buildCaseNotesApiModel",
    input = "casenotes-api-docs.json",
    output = "casenotes",
    packageName = "casenotes",
  ),
  // https://prersonal-relationships-api-dev.hmpps.service.justice.gov.uk/v3/api-docs
  ModelConfiguration(
    name = "buildPersonalRelationshipsApiModel",
    input = "personal-relationships-api-docs.json",
    output = "personalrelationships",
    packageName = "personalrelationships",
  ),
  // https://organisations-api-dev.hmpps.service.justice.gov.uk/v3/api-docs
  ModelConfiguration(
    name = "buildOrganisationsApiModel",
    input = "organisations-api-docs.json",
    output = "organisations",
    packageName = "organisations",
  ),
  // https://hmpps-person-record-dev.hmpps.service.justice.gov.uk/v3/api-docs
  ModelConfiguration(
    name = "buildCorePersonApiModel",
    input = "core-person-api-docs.json",
    output = "coreperson",
    packageName = "coreperson",
  ),
  // https://remand-and-sentencing-api-dev.hmpps.service.justice.gov.uk/v3/api-docs
  ModelConfiguration(
    name = "buildCourtSentencingApiModel",
    input = "court-sentencing-api-docs.json",
    output = "courtsentencing",
    packageName = "courtsentencing",
  ),
  // https://csip-api-dev.prison.service.justice.gov.uk/v3/api-docs
  ModelConfiguration(
    name = "buildCsipApiModel",
    input = "csip-api-docs.json",
    output = "csip",
    packageName = "csip",
  ),
  // https://incidents-api-dev.prison.service.justice.gov.uk/v3/api-docs
  ModelConfiguration(
    name = "buildIncidentsApiModel",
    input = "incidents-api-docs.json",
    output = "incidents",
    packageName = "incidents",
  ),
  // https://locations-api-dev.prison.service.justice.gov.uk/v3/api-docs
  ModelConfiguration(
    name = "buildLocationsApiModel",
    input = "locations-api-docs.json",
    output = "locations",
    packageName = "locations",
  ),
  // https://nomis-prisoner-api-dev.prison.service.justice.gov.uk/v3/api-docs
  ModelConfiguration(
    name = "buildNomisSyncApiModel",
    input = "nomis-sync-api-docs.json",
    output = "nomissync",
    packageName = "nomissync",
  ),
  // https://nomis-sync-prisoner-mapping-dev.hmpps.service.justice.gov.uk/v3/api-docs
  ModelConfiguration(
    name = "buildMappingServiceApiModel",
    input = "nomis-mapping-service-api-docs.json",
    output = "mappings",
    packageName = "nomismappings",
  ),
  // https://prison-person-api-dev.prison.service.justice.gov.uk/v3/api-docs
  ModelConfiguration(
    name = "buildPrisonPersonApiModel",
    input = "prison-person-api-docs.json",
    output = "prisonperson",
    packageName = "prisonperson",
  ),
  // https://adjustments-api-dev.hmpps.service.justice.gov.uk/v3/api-docs
  ModelConfiguration(
    name = "buildSentencingAdjustmentsApiModel",
    input = "sentencing-adjustments-api-docs.json",
    output = "sentencingadjustments",
    packageName = "sentencing.adjustments",
  ),
)

tasks {
  withType<KotlinCompile> {
    dependsOn(models.map { it.name })
    compilerOptions.jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
  }
  withType<KtLintCheckTask> {
    // Under gradle 8 we must declare the dependency here, even if we're not going to be linting the model
    mustRunAfter(models.map { it.name })
  }
  withType<KtLintFormatTask> {
    // Under gradle 8 we must declare the dependency here, even if we're not going to be linting the model
    mustRunAfter(models.map { it.name })
  }
}
models.forEach {
  tasks.register(it.name, GenerateTask::class) {
    generatorName.set("kotlin")
    skipValidateSpec.set(true)
    inputSpec.set("openapi-specs/${it.input}")
    outputDir.set("$buildDirectory/generated/${it.output}")
    modelPackage.set("uk.gov.justice.digital.hmpps.prisonerfromnomismigration.${it.packageName}.model")
    apiPackage.set("uk.gov.justice.digital.hmpps.prisonerfromnomismigration.${it.packageName}.api")
    configOptions.set(configValues)
    globalProperties.set(mapOf("models" to ""))
    generateModelTests.set(false)
    generateModelDocumentation.set(false)
  }
}

val buildDirectory: Directory = layout.buildDirectory.get()
val configValues = mapOf(
  "dateLibrary" to "java8-localdatetime",
  "serializationLibrary" to "jackson",
  "enumPropertyNaming" to "original",
)

kotlin {
  models.map { it.output }.forEach { generatedProject ->
    sourceSets["main"].apply {
      kotlin.srcDir("$buildDirectory/generated/$generatedProject/src/main/kotlin")
    }
  }
}

configure<KtlintExtension> {
  models.map { it.output }.forEach { generatedProject ->
    filter {
      exclude {
        it.file.path.contains("$buildDirectory/generated/$generatedProject/src/main/")
      }
    }
  }
}
