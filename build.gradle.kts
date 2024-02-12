import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import org.jlleitschuh.gradle.ktlint.tasks.KtLintCheckTask
import org.jlleitschuh.gradle.ktlint.tasks.KtLintFormatTask
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "5.15.1"
  kotlin("plugin.spring") version "1.9.22"
  id("org.openapi.generator") version "7.2.0"
}

configurations {
  implementation { exclude(module = "spring-boot-starter-web") }
  implementation { exclude(module = "spring-boot-starter-tomcat") }
  testImplementation { exclude(group = "org.junit.vintage") }
}

dependencies {
  implementation("uk.gov.justice.service.hmpps:hmpps-kotlin-spring-boot-starter:0.0.4")
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("uk.gov.justice.service.hmpps:hmpps-sqs-spring-boot-starter:2.2.1")
  implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")

  implementation("org.springdoc:springdoc-openapi-starter-webflux-ui:2.3.0")

  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk9")

  runtimeOnly("org.postgresql:r2dbc-postgresql:1.0.4.RELEASE")
  runtimeOnly("org.springframework.boot:spring-boot-starter-jdbc")
  runtimeOnly("org.postgresql:postgresql:42.7.1")
  implementation("org.flywaydb:flyway-core")

  implementation("io.opentelemetry.instrumentation:opentelemetry-instrumentation-annotations:1.32.0")

  testImplementation("io.swagger.parser.v3:swagger-parser:2.1.20") {
    exclude(group = "io.swagger.core.v3")
  }
  testImplementation("io.swagger.core.v3:swagger-core-jakarta:2.2.20")
  testImplementation("io.jsonwebtoken:jjwt-impl:0.12.3")
  testImplementation("io.jsonwebtoken:jjwt-jackson:0.12.3")

  testImplementation("org.wiremock:wiremock-standalone:3.3.1")
  testImplementation("org.testcontainers:localstack:1.19.3")
  testImplementation("com.amazonaws:aws-java-sdk-core:1.12.643")
  testImplementation("org.awaitility:awaitility-kotlin:4.2.0")
  testImplementation("org.testcontainers:postgresql:1.19.3")
  testImplementation("io.mockk:mockk:1.13.9")
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
      "buildAdjudicationApiModel",
      "buildIncidentsApiModel",
      "buildMappingServiceApiModel",
      "buildSentencingAdjustmentsApiModel",
    )
    kotlinOptions {
      jvmTarget = "21"
    }
  }
  withType<KtLintCheckTask> {
    // Under gradle 8 we must declare the dependency here, even if we're not going to be linting the model
    mustRunAfter(
      "buildActivityApiModel",
      "buildNomisSyncApiModel",
      "buildAdjudicationApiModel",
      "buildIncidentsApiModel",
      "buildMappingServiceApiModel",
      "buildSentencingAdjustmentsApiModel",
    )
  }
  withType<KtLintFormatTask> {
    // Under gradle 8 we must declare the dependency here, even if we're not going to be linting the model
    mustRunAfter(
      "buildActivityApiModel",
      "buildNomisSyncApiModel",
      "buildAdjudicationApiModel",
      "buildIncidentsApiModel",
      "buildMappingServiceApiModel",
      "buildSentencingAdjustmentsApiModel",
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
  globalProperties.set(
    mapOf(
      "models" to "",
    ),
  )
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

tasks.register("buildAdjudicationApiModel", GenerateTask::class) {
  generatorName.set("kotlin")
  skipValidateSpec.set(true)
  inputSpec.set("openapi-specs/adjudications-api-docs.json")
  outputDir.set("$buildDirectory/generated/adjudications")
  modelPackage.set("uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model")
  apiPackage.set("uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.api")
  configOptions.set(configValues)
  globalProperties.set(mapOf("models" to ""))
}

tasks.register("buildIncidentsApiModel", GenerateTask::class) {
  generatorName.set("kotlin")
  inputSpec.set("openapi-specs/incidents-api-docs.json")
  outputDir.set("$buildDirectory/generated/incidents")
  modelPackage.set("uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.model")
  apiPackage.set("uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.api")
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

/* TODO when sentencing API created

tasks.register("buildSentencingApiModel", GenerateTask::class) {
  generatorName.set("kotlin")
  inputSpec.set("sentences-api-docs.json")
  outputDir.set("$buildDirectory/generated/sentencing")
  modelPackage.set("uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing.model")
  apiPackage.set("uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing.api")
  configOptions.set(
    mapOf(
      "dateLibrary" to "java8-localdatetime",
      "serializationLibrary" to "jackson"
    )
  )
  globalProperties.set(
    mapOf(
      "models" to ""
    )
  )
}
*/

val generatedProjectDirs =
  listOf("activities", "adjudications", "incidents", "nomissync", "mappings", "sentencingadjustments")

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
