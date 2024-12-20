import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import org.jlleitschuh.gradle.ktlint.tasks.KtLintCheckTask
import org.jlleitschuh.gradle.ktlint.tasks.KtLintFormatTask
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "6.1.0"
  kotlin("plugin.spring") version "2.0.21"
  id("org.openapi.generator") version "7.10.0"
}

configurations {
  implementation { exclude(module = "spring-boot-starter-web") }
  implementation { exclude(module = "spring-boot-starter-tomcat") }
  testImplementation { exclude(group = "org.junit.vintage") }
}

dependencies {
  implementation("uk.gov.justice.service.hmpps:hmpps-kotlin-spring-boot-starter:1.1.0")
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("uk.gov.justice.service.hmpps:hmpps-sqs-spring-boot-starter:5.2.0")
  implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")

  implementation("org.springdoc:springdoc-openapi-starter-webflux-ui:2.7.0")

  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk9")

  runtimeOnly("org.postgresql:r2dbc-postgresql:1.0.7.RELEASE")
  runtimeOnly("org.springframework.boot:spring-boot-starter-jdbc")
  runtimeOnly("org.postgresql:postgresql:42.7.4")
  runtimeOnly("org.flywaydb:flyway-database-postgresql")
  implementation("io.opentelemetry:opentelemetry-extension-kotlin:1.43.0")

  implementation("io.opentelemetry.instrumentation:opentelemetry-instrumentation-annotations:2.9.0")

  testImplementation("uk.gov.justice.service.hmpps:hmpps-kotlin-spring-boot-starter-test:1.1.0")
  testImplementation("io.swagger.parser.v3:swagger-parser:2.1.24") {
    exclude(group = "io.swagger.core.v3")
  }
  testImplementation("io.swagger.core.v3:swagger-core-jakarta:2.2.26")

  testImplementation("org.wiremock:wiremock-standalone:3.9.2")
  testImplementation("org.testcontainers:localstack:1.20.4")
  testImplementation("com.amazonaws:aws-java-sdk-core:1.12.778")
  testImplementation("org.awaitility:awaitility-kotlin:4.2.2")
  testImplementation("org.testcontainers:postgresql:1.20.4")
  testImplementation("io.mockk:mockk:1.13.13")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
}

kotlin {
  jvmToolchain(21)
}

tasks {
  withType<KotlinCompile> {
    //  dependsOn("buildSentencingApiModel")
    dependsOn(
      "buildActivityApiModel",
      "buildNomisSyncApiModel",
      "buildIncidentsApiModel",
      "buildCsipApiModel",
      "buildLocationsApiModel",
      "buildCaseNotesApiModel",
      "buildMappingServiceApiModel",
      "buildSentencingAdjustmentsApiModel",
      "buildAlertsApiModel",
      "buildCourtSentencingApiModel",
      "buildPrisonPersonApiModel",
      "buildContactPersonApiModel",
    )
    compilerOptions.jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
  }
  withType<KtLintCheckTask> {
    // Under gradle 8 we must declare the dependency here, even if we're not going to be linting the model
    mustRunAfter(
      "buildActivityApiModel",
      "buildNomisSyncApiModel",
      "buildIncidentsApiModel",
      "buildCsipApiModel",
      "buildLocationsApiModel",
      "buildCaseNotesApiModel",
      "buildMappingServiceApiModel",
      "buildSentencingAdjustmentsApiModel",
      "buildAlertsApiModel",
      "buildCourtSentencingApiModel",
      "buildPrisonPersonApiModel",
      "buildContactPersonApiModel",
    )
  }
  withType<KtLintFormatTask> {
    // Under gradle 8 we must declare the dependency here, even if we're not going to be linting the model
    mustRunAfter(
      "buildActivityApiModel",
      "buildNomisSyncApiModel",
      "buildIncidentsApiModel",
      "buildCsipApiModel",
      "buildLocationsApiModel",
      "buildCaseNotesApiModel",
      "buildMappingServiceApiModel",
      "buildSentencingAdjustmentsApiModel",
      "buildAlertsApiModel",
      "buildCourtSentencingApiModel",
      "buildPrisonPersonApiModel",
      "buildContactPersonApiModel",
    )
  }
}

val buildDirectory: Directory = layout.buildDirectory.get()
val configValues = mapOf(
  "dateLibrary" to "java8-localdatetime",
  "serializationLibrary" to "jackson",
  "enumPropertyNaming" to "original",
)

tasks.register("buildActivityApiModel", GenerateTask::class) {
  generatorName.set("kotlin")
  skipValidateSpec.set(true)
  inputSpec.set("openapi-specs/activities-api-docs.json")
  // remoteInputSpec.set("https://activities-api-dev.prison.service.justice.gov.uk/v3/api-docs")
  outputDir.set("$buildDirectory/generated/activities")
  modelPackage.set("uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities.model")
  apiPackage.set("uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities.api")
  configOptions.set(configValues)
  globalProperties.set(mapOf("models" to ""))
}

tasks.register("buildNomisSyncApiModel", GenerateTask::class) {
  generatorName.set("kotlin")
  inputSpec.set("openapi-specs/nomis-sync-api-docs.json")
  // remoteInputSpec.set("https://prisoner-to-nomis-update-dev.hmpps.service.justice.gov.uk/v3/api-docs")
  outputDir.set("$buildDirectory/generated/nomissync")
  modelPackage.set("uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model")
  apiPackage.set("uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.api")
  configOptions.set(configValues)
  globalProperties.set(mapOf("models" to ""))
}

tasks.register("buildMappingServiceApiModel", GenerateTask::class) {
  generatorName.set("kotlin")
  inputSpec.set("openapi-specs/nomis-mapping-service-api-docs.json")
  outputDir.set("$buildDirectory/generated/mappings")
  modelPackage.set("uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model")
  apiPackage.set("uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.api")
  configOptions.set(configValues)
  globalProperties.set(mapOf("models" to ""))
}

tasks.register("buildIncidentsApiModel", GenerateTask::class) {
  generatorName.set("kotlin")
  skipValidateSpec.set(true)
  inputSpec.set("openapi-specs/incidents-api-docs.json")
  outputDir.set("$buildDirectory/generated/incidents")
  modelPackage.set("uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.model")
  apiPackage.set("uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.api")
  configOptions.set(configValues)
  globalProperties.set(mapOf("models" to ""))
}

tasks.register("buildCsipApiModel", GenerateTask::class) {
  generatorName.set("kotlin")
  inputSpec.set("openapi-specs/csip-api-docs.json")
  outputDir.set("$buildDirectory/generated/csip")
  modelPackage.set("uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model")
  apiPackage.set("uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.api")
  configOptions.set(configValues)
  globalProperties.set(mapOf("models" to ""))
}

tasks.register("buildLocationsApiModel", GenerateTask::class) {
  generatorName.set("kotlin")
  inputSpec.set("openapi-specs/locations-api-docs.json")
  outputDir.set("$buildDirectory/generated/locations")
  modelPackage.set("uk.gov.justice.digital.hmpps.prisonerfromnomismigration.locations.model")
  apiPackage.set("uk.gov.justice.digital.hmpps.prisonerfromnomismigration.locations.api")
  configOptions.set(configValues)
  globalProperties.set(mapOf("models" to ""))
}

tasks.register("buildCaseNotesApiModel", GenerateTask::class) {
  generatorName.set("kotlin")
  inputSpec.set("openapi-specs/casenotes-api-docs.json")
  outputDir.set("$buildDirectory/generated/casenotes")
  modelPackage.set("uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes.model")
  apiPackage.set("uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes.api")
  configOptions.set(configValues)
  globalProperties.set(mapOf("models" to ""))
}

tasks.register("buildSentencingAdjustmentsApiModel", GenerateTask::class) {
  generatorName.set("kotlin")
  skipValidateSpec.set(true)
  inputSpec.set("openapi-specs/sentencing-adjustments-api-docs.json")
  outputDir.set("$buildDirectory/generated/sentencingadjustments")
  modelPackage.set("uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing.adjustments.model")
  apiPackage.set("uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing.adjustments.api")
  configOptions.set(configValues)
  globalProperties.set(mapOf("models" to ""))
}

tasks.register("buildAlertsApiModel", GenerateTask::class) {
  generatorName.set("kotlin")
  skipValidateSpec.set(true)
  inputSpec.set("openapi-specs/alerts-api-docs.json")
  outputDir.set("$buildDirectory/generated/alerts")
  modelPackage.set("uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts.model")
  apiPackage.set("uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts.api")
  configOptions.set(configValues)
  globalProperties.set(mapOf("models" to ""))
}

tasks.register("buildCourtSentencingApiModel", GenerateTask::class) {
  generatorName.set("kotlin")
  skipValidateSpec.set(true)
  inputSpec.set("openapi-specs/court-sentencing-api-docs.json")
  outputDir.set("$buildDirectory/generated/courtsentencing")
  modelPackage.set("uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model")
  apiPackage.set("uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.api")
  configOptions.set(configValues)
  globalProperties.set(mapOf("models" to ""))
}

tasks.register("buildPrisonPersonApiModel", GenerateTask::class) {
  generatorName.set("kotlin")
  skipValidateSpec.set(true)
  inputSpec.set("openapi-specs/prison-person-api-docs.json")
  outputDir.set("$buildDirectory/generated/prisonperson")
  modelPackage.set("uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.model")
  apiPackage.set("uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.api")
  configOptions.set(configValues)
  globalProperties.set(mapOf("models" to ""))
}

tasks.register("buildContactPersonApiModel", GenerateTask::class) {
  generatorName.set("kotlin")
  skipValidateSpec.set(true)
  inputSpec.set("openapi-specs/contact-person-api-docs.json")
  outputDir.set("$buildDirectory/generated/contactperson")
  modelPackage.set("uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model")
  apiPackage.set("uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.api")
  configOptions.set(configValues)
  globalProperties.set(mapOf("models" to ""))
}

val generatedProjectDirs =
  listOf(
    "activities", "incidents", "csip", "locations", "casenotes",
    "nomissync", "mappings", "sentencingadjustments", "alerts", "courtsentencing", "prisonperson", "contactperson",
  )

kotlin {
  generatedProjectDirs.forEach { generatedProject ->
    sourceSets["main"].apply {
      kotlin.srcDir("$buildDirectory/generated/$generatedProject/src/main/kotlin")
    }
  }
}

configure<KtlintExtension> {
  generatedProjectDirs.forEach { generatedProject ->
    filter {
      exclude {
        it.file.path.contains("$buildDirectory/generated/$generatedProject/src/main/")
      }
    }
  }
}
