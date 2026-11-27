package uk.gov.justice.digital.hmpps.testselection

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import uk.gov.justice.digital.hmpps.testselection.Impact.All
import uk.gov.justice.digital.hmpps.testselection.Impact.Feature
import uk.gov.justice.digital.hmpps.testselection.Impact.None
import uk.gov.justice.digital.hmpps.testselection.Impact.Unclassified

class PathRulesTest {
  private val prefix = "uk.gov.justice.digital.hmpps.prisonerfromnomismigration"
  private val base = "uk/gov/justice/digital/hmpps/prisonerfromnomismigration"
  private val models = listOf(
    ModelConfiguration(name = "agency-registers", packageName = "agencyregisters", testPackageName = "agency", url = "http://localhost"),
    ModelConfiguration(name = "sentencing-adjustments", packageName = "sentencing.adjustments", testPackageName = "sentencing", url = "http://localhost"),
    ModelConfiguration(name = "visit-balance", packageName = "visit.balance", testPackageName = "visitbalances", url = "http://localhost"),
    ModelConfiguration(name = "alerts", packageName = "alerts", testPackageName = "alerts", url = "http://localhost"),
    ModelConfiguration(name = "property", packageName = "property", url = "http://localhost"),
    ModelConfiguration(name = "nomis-prisoner", packageName = "nomisprisoner", url = "http://localhost"),
    ModelConfiguration(name = "nomis-mapping-service", packageName = "nomismappings", url = "http://localhost"),
  )

  private fun impact(path: String) = classify(path.replace("<base>", base), models, prefix).impact

  @ParameterizedTest
  @CsvSource(
    ".github/workflows/nightly_tests.yml",
    ".github/dependabot.yml",
    "helm_deploy/values-dev.yaml",
    "doc/design.puml",
    "README.md",
    "src/main/kotlin/<base>/alerts/README.md",
    "LICENSE",
    ".snyk",
    "sonar-project.properties",
    "Dockerfile",
    "applicationinsights.dev.json",
    "check-api-docs.bash",
    "setup-test-executor.sh",
    ".gitignore",
    "docker-compose-test.yml",
    "openapi-specs/nomis-sync-api-docs.json",
    "openapi-generator-ignore-nomis-sync",
  )
  fun `paths that cannot affect the tests`(path: String) {
    assertThat(impact(path)).isEqualTo(None)
  }

  @ParameterizedTest
  @CsvSource(
    ".github/workflows/setup_test_matrix.yml",
    ".github/workflows/pipeline.yml",
    "src/main/kotlin/<base>/service/Thing.kt",
    "src/test/kotlin/<base>/helpers/Thing.kt",
    "src/test/kotlin/<base>/helper/Thing.kt",
    "src/main/kotlin/<base>/config/Thing.kt",
    "src/main/kotlin/<base>/data/Thing.kt",
    "src/main/kotlin/<base>/listeners/Thing.kt",
    "src/test/kotlin/<base>/integration/Thing.kt",
    "src/main/kotlin/<base>/persistence/Thing.kt",
    "src/test/kotlin/<base>/wiremock/Thing.kt",
    "src/main/kotlin/<base>/nomisprisoner/Thing.kt",
    "src/main/kotlin/<base>/nomismappings/Thing.kt",
    "src/main/kotlin/<base>/HmppsPrisonerFromNomisMigration.kt",
    "src/main/resources/application.yml",
    "src/test/resources/logback-test.xml",
    "build.gradle.kts",
    "settings.gradle.kts",
    "gradle/libs.versions.toml",
    "gradle.properties",
    "gradlew",
    "gradlew.bat",
    ".java-version",
    "build-logic/build.gradle.kts",
    "openapi-specs/nomis-prisoner-api-docs.json",
    "openapi-specs/nomis-mapping-service-api-docs.json",
    "openapi-generator-ignore-nomis-prisoner",
    "openapi-generator-ignore-nomis-mapping-service",
  )
  fun `paths that affect every suite`(path: String) {
    assertThat(impact(path)).isEqualTo(All)
  }

  @ParameterizedTest
  @CsvSource(
    "src/main/kotlin/<base>/alerts/AlertsService.kt,testAlerts",
    "src/test/kotlin/<base>/alerts/AlertsTest.kt,testAlerts",
    "src/test/kotlin/<base>/agency/AgencyTest.kt,testAgencyRegisters",
    "src/main/kotlin/<base>/sentencing/Thing.kt,testSentencingAdjustments",
    "src/main/kotlin/<base>/visitbalances/Thing.kt,testVisitBalance",
    "src/main/kotlin/<base>/health/Thing.kt,test",
    "src/test/kotlin/<base>/drugtesting/Thing.kt,test",
    "openapi-specs/alerts-api-docs.json,testAlerts",
    "openapi-generator-ignore-alerts,testAlerts",
    "openapi-specs/agency-registers-api-docs.json,testAgencyRegisters",
    "openapi-generator-ignore-visit-balance,testVisitBalance",
    "openapi-specs/property-api-docs.json,test",
  )
  fun `paths that affect one suite`(path: String, task: String) {
    assertThat(impact(path)).isEqualTo(Feature(task))
  }

  @ParameterizedTest
  @CsvSource(
    "something-new.txt",
    "src/main/kotlin/org/openapitools/client/infrastructure/ApiClient.kt",
    "src/main/kotlin/other/Thing.kt",
    "dps-gradle-spring-boot-suppressions.xml",
  )
  fun `anything else is unclassified`(path: String) {
    assertThat(impact(path)).isEqualTo(Unclassified)
  }

  @ParameterizedTest
  @CsvSource(
    "helm_deploy/values-dev.yaml,helm_deploy",
    "src/main/kotlin/<base>/alerts/AlertsService.kt,alerts",
    "something-new.txt,unclassified",
  )
  fun `every file gets a reason`(path: String, expected: String) {
    assertThat(classify(path.replace("<base>", base), models, prefix).reason).containsIgnoringCase(expected)
  }
}
