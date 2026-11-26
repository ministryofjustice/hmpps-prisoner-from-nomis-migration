plugins {
  `kotlin-dsl`
  id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
}

repositories {
  mavenCentral()
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
  testImplementation(platform("org.junit:junit-bom:6.0.3"))
  testImplementation("org.junit.jupiter:junit-jupiter")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
  testImplementation("org.assertj:assertj-core:3.27.7")
}

tasks.test {
  useJUnitPlatform()
}
