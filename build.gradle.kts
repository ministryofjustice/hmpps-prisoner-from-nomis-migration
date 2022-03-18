plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "4.0.5-beta"
  kotlin("plugin.spring") version "1.6.10"
}

configurations {
  testImplementation { exclude(group = "org.junit.vintage") }
}

dependencies {
  implementation("org.springframework.boot:spring-boot-starter-webflux")

  implementation("org.springframework.boot:spring-boot-starter-security")
  implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
  implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
  implementation("uk.gov.justice.service.hmpps:hmpps-sqs-spring-boot-starter:1.1.1")
  implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")

  implementation("org.springdoc:springdoc-openapi-ui:1.6.6")
  implementation("org.springdoc:springdoc-openapi-kotlin:1.6.6")
  implementation("org.springdoc:springdoc-openapi-data-rest:1.6.6")
  implementation("org.springdoc:springdoc-openapi-security:1.6.6")

  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.0")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.6.0")

  runtimeOnly("io.r2dbc:r2dbc-postgresql")
  runtimeOnly("org.springframework.boot:spring-boot-starter-jdbc")
  runtimeOnly("org.postgresql:postgresql:42.3.3")
  implementation("org.flywaydb:flyway-core:8.5.1")

  testImplementation("io.swagger.parser.v3:swagger-parser:2.0.30")
  testImplementation("io.jsonwebtoken:jjwt:0.9.1")
  testImplementation("com.github.tomakehurst:wiremock-standalone:2.27.2")
  testImplementation("org.testcontainers:localstack:1.16.3")
  testImplementation("org.awaitility:awaitility-kotlin:4.2.0")
  testImplementation("org.testcontainers:postgresql:1.16.3")
}

java {
  toolchain.languageVersion.set(JavaLanguageVersion.of(17))
}

tasks {
  withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
      jvmTarget = "17"
    }
  }
}
