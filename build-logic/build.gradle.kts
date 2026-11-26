plugins {
  `kotlin-dsl`
  id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
}

repositories {
  mavenCentral()
  gradlePluginPortal()
}

kotlin {
  jvmToolchain(25)
}

gradlePlugin {
  plugins {
    register("selectiveTests") {
      id = "uk.gov.justice.digital.hmpps.selective-tests"
      implementationClass = "uk.gov.justice.digital.hmpps.testselection.SelectiveTestsPlugin"
    }
  }
}

dependencies {
  // Plugins the consuming build already applies, so compile against them but use the consumer's copies at runtime
  compileOnly("org.openapitools:openapi-generator-gradle-plugin:7.27.0")
  compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21")
  compileOnly("org.jlleitschuh.gradle:ktlint-gradle:14.2.0")
  implementation("tools.jackson.core:jackson-databind:3.1.8")

  testImplementation("org.openapitools:openapi-generator-gradle-plugin:7.27.0")
  testImplementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21")
  testImplementation("org.jlleitschuh.gradle:ktlint-gradle:14.2.0")
  testImplementation(platform("org.junit:junit-bom:6.0.3"))
  testImplementation("org.junit.jupiter:junit-jupiter")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
  testImplementation("org.assertj:assertj-core:3.27.7")
}

tasks.test {
  useJUnitPlatform()
}
