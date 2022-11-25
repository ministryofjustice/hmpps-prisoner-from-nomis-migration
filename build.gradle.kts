plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "4.7.0-beta"
  kotlin("plugin.spring") version "1.7.21"
}

dependencyCheck {
  suppressionFiles.add("reactive-suppressions.xml")
  // Please remove the below suppressions once it has been suppressed in the DependencyCheck plugin (see this issue: https://github.com/jeremylong/DependencyCheck/issues/4616)
  suppressionFiles.add("postgres-suppressions.xml")
}

configurations {
  implementation { exclude(module = "spring-boot-starter-web") }
  implementation { exclude(module = "spring-boot-starter-tomcat") }
  testImplementation { exclude(group = "org.junit.vintage") }
}

// Temporarily kept at 0.9.2 as get class java.lang.Long cannot be cast to class java.lang.Integer when upgrading to 1.0.0.RELEASE
val r2dbcPostgresVersion by extra("0.9.2.RELEASE")

dependencies {
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springframework.boot:spring-boot-starter-security")
  implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
  implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
  implementation("uk.gov.justice.service.hmpps:hmpps-sqs-spring-boot-starter:1.1.13")
  implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")

  implementation("org.springdoc:springdoc-openapi-webflux-ui:1.6.13")
  implementation("org.springdoc:springdoc-openapi-kotlin:1.6.13")
  implementation("org.springdoc:springdoc-openapi-security:1.6.13")

  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.6.4")
  implementation("io.jsonwebtoken:jjwt:0.9.1")
  implementation("io.opentelemetry:opentelemetry-api:1.20.1")
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.14.1")

  runtimeOnly("org.postgresql:r2dbc-postgresql:$r2dbcPostgresVersion")
  runtimeOnly("org.springframework.boot:spring-boot-starter-jdbc")
  runtimeOnly("org.postgresql:postgresql:42.5.1")
  implementation("org.flywaydb:flyway-core")

  testImplementation("io.swagger.parser.v3:swagger-parser:2.1.9")

  testImplementation("com.github.tomakehurst:wiremock-standalone:2.27.2")
  testImplementation("org.testcontainers:localstack:1.17.6")
  testImplementation("org.awaitility:awaitility-kotlin:4.2.0")
  testImplementation("org.testcontainers:postgresql:1.17.6")
  testImplementation("io.mockk:mockk:1.13.2")
}

java {
  toolchain.languageVersion.set(JavaLanguageVersion.of(18))
}

tasks {
  withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
      jvmTarget = "18"
    }
  }
}
