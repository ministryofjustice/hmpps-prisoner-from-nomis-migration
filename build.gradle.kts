import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import org.jlleitschuh.gradle.ktlint.tasks.KtLintCheckTask
import org.jlleitschuh.gradle.ktlint.tasks.KtLintFormatTask
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "5.5.1"
  kotlin("plugin.spring") version "1.9.10"
  id("org.openapi.generator") version "7.0.1"
}

configurations {
  implementation { exclude(module = "spring-boot-starter-web") }
  implementation { exclude(module = "spring-boot-starter-tomcat") }
  testImplementation { exclude(group = "org.junit.vintage") }
}

dependencies {
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springframework.boot:spring-boot-starter-security")
  implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
  implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
  implementation("uk.gov.justice.service.hmpps:hmpps-sqs-spring-boot-starter:2.1.1")
  implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")

  implementation("org.springdoc:springdoc-openapi-starter-webflux-ui:2.2.0")

  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk9")

  implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2")

  runtimeOnly("org.postgresql:r2dbc-postgresql:1.0.2.RELEASE")
  runtimeOnly("org.springframework.boot:spring-boot-starter-jdbc")
  runtimeOnly("org.postgresql:postgresql:42.6.0")
  implementation("org.flywaydb:flyway-core")

  implementation("io.opentelemetry.instrumentation:opentelemetry-instrumentation-annotations:1.30.0")

  testImplementation("io.swagger.parser.v3:swagger-parser:2.1.16")
  testImplementation("io.jsonwebtoken:jjwt-impl:0.12.2")
  testImplementation("io.jsonwebtoken:jjwt-jackson:0.12.2")

  testImplementation("org.wiremock:wiremock:3.2.0")
  testImplementation("org.testcontainers:localstack:1.19.1")
  testImplementation("com.amazonaws:aws-java-sdk-core:1.12.565")
  testImplementation("org.awaitility:awaitility-kotlin:4.2.0")
  testImplementation("org.testcontainers:postgresql:1.19.1")
  testImplementation("io.mockk:mockk:1.13.8")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
  testImplementation("javax.xml.bind:jaxb-api:2.3.1")
}

java {
  toolchain.languageVersion.set(JavaLanguageVersion.of(20))
}

tasks {
  withType<KotlinCompile> {
    //  dependsOn("buildSentencingApiModel")
    dependsOn("buildActivityApiModel", "buildNomisSyncApiModel", "buildAdjudicationApiModel", "buildNonAssociationsApiModel", "buildMappingServiceApiModel")
    kotlinOptions {
      jvmTarget = "20"
    }
  }
  withType<KtLintCheckTask> {
    // Under gradle 8 we must declare the dependency here, even if we're not going to be linting the model
    mustRunAfter("buildActivityApiModel", "buildNomisSyncApiModel", "buildAdjudicationApiModel", "buildNonAssociationsApiModel", "buildMappingServiceApiModel")
  }
  withType<KtLintFormatTask> {
    // Under gradle 8 we must declare the dependency here, even if we're not going to be linting the model
    mustRunAfter("buildActivityApiModel", "buildNomisSyncApiModel", "buildAdjudicationApiModel", "buildNonAssociationsApiModel", "buildMappingServiceApiModel")
  }
}

tasks.register("buildActivityApiModel", GenerateTask::class) {
  generatorName.set("kotlin")
  skipValidateSpec.set(true)
  inputSpec.set("openapi-specs/activities-api-docs.json")
  // remoteInputSpec.set("https://activities-api-dev.prison.service.justice.gov.uk/v3/api-docs")
  outputDir.set("$buildDir/generated")
  modelPackage.set("uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities.model")
  apiPackage.set("uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities.api")
  configOptions.set(
    mapOf(
      "dateLibrary" to "java8-localdatetime",
      "serializationLibrary" to "jackson",
      "enumPropertyNaming" to "original"
    )
  )
  globalProperties.set(mapOf("models" to ""))
}

tasks.register("buildNomisSyncApiModel", GenerateTask::class) {
  generatorName.set("kotlin")
  inputSpec.set("openapi-specs/nomis-sync-api-docs.json")
  // remoteInputSpec.set("https://prisoner-to-nomis-update-dev.hmpps.service.justice.gov.uk/v3/api-docs")
  outputDir.set("$buildDir/generated")
  modelPackage.set("uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model")
  apiPackage.set("uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.api")
  configOptions.set(
    mapOf(
      "dateLibrary" to "java8-localdatetime",
      "serializationLibrary" to "jackson",
      "enumPropertyNaming" to "original"
    )
  )
  globalProperties.set(
    mapOf(
      "models" to ""
    )
  )
}

tasks.register("buildMappingServiceApiModel", GenerateTask::class) {
  generatorName.set("kotlin")
  inputSpec.set("openapi-specs/nomis-mapping-service-api-docs.json")
  outputDir.set("$buildDir/generated")
  modelPackage.set("uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model")
  apiPackage.set("uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.api")
  configOptions.set(
    mapOf(
      "dateLibrary" to "java8-localdatetime",
      "serializationLibrary" to "jackson",
      "enumPropertyNaming" to "original"
    )
  )
  globalProperties.set(mapOf("models" to ""))
}

tasks.register("buildAdjudicationApiModel", GenerateTask::class) {
  generatorName.set("kotlin")
  skipValidateSpec.set(true)
  inputSpec.set("openapi-specs/adjudications-api-docs.json")
  outputDir.set("$buildDir/generated")
  modelPackage.set("uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model")
  apiPackage.set("uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.api")
  configOptions.set(
    mapOf(
      "dateLibrary" to "java8-localdatetime",
      "serializationLibrary" to "jackson",
      "enumPropertyNaming" to "original"
    )
  )
  globalProperties.set(mapOf("models" to ""))
}

tasks.register("buildNonAssociationsApiModel", GenerateTask::class) {
  generatorName.set("kotlin")
  inputSpec.set("openapi-specs/non-associations-api-docs.json")
  outputDir.set("$buildDir/generated")
  modelPackage.set("uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nonassociations.model")
  apiPackage.set("uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nonassociations.api")
  configOptions.set(
    mapOf(
      "dateLibrary" to "java8-localdatetime",
      "serializationLibrary" to "jackson",
      "enumPropertyNaming" to "original"
    )
  )
  globalProperties.set(mapOf("models" to ""))
}

/* TODO when sentencing API created

tasks.register("buildSentencingApiModel", GenerateTask::class) {
  generatorName.set("kotlin")
  inputSpec.set("sentences-api-docs.json")
  outputDir.set("$buildDir/generated")
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

kotlin {
  sourceSets["main"].apply {
    kotlin.srcDir("$buildDir/generated/src/main/kotlin")
  }
}

configure<KtlintExtension> {
  filter {
    exclude {
      it.file.path.contains("build/generated/src/main/")
    }
  }
}
