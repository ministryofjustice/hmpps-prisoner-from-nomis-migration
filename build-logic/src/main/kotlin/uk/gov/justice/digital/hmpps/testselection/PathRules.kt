package uk.gov.justice.digital.hmpps.testselection

import uk.gov.justice.digital.hmpps.testselection.Impact.All
import uk.gov.justice.digital.hmpps.testselection.Impact.Feature
import uk.gov.justice.digital.hmpps.testselection.Impact.None
import uk.gov.justice.digital.hmpps.testselection.Impact.Unclassified

sealed interface Impact {
  data object None : Impact
  data object All : Impact
  data class Feature(val testTask: String) : Impact
  data object Unclassified : Impact
}

data class Classification(val path: String, val impact: Impact, val reason: String)

private val selectorWorkflows = setOf(".github/workflows/setup_test_matrix.yml", ".github/workflows/pipeline.yml")
private val noneFiles = setOf("LICENSE", ".snyk", "sonar-project.properties", "Dockerfile", ".gitignore")
private val sharedPackages = setOf("service", "helpers", "helper", "config", "data", "listeners", "integration", "persistence", "wiremock", "nomisprisoner", "nomismappings")
private val allFiles = setOf("build.gradle.kts", "settings.gradle.kts", "gradle.properties", ".java-version")

// the schemas of these specs are shared, mapping them to features is a later step
private val sharedSpecs = setOf("nomis-prisoner", "nomis-mapping-service")
private val applicationInsights = "applicationinsights.*\\.json".toRegex()
private val specRegex = "openapi-specs/(.+)-api-docs\\.json".toRegex()
private val ignoreRegex = "openapi-generator-ignore-(.+)".toRegex()

fun classify(path: String, models: List<ModelConfiguration>, packagePrefix: String): Classification {
  val sourceRoot = Regex("src/(?:main|test)/kotlin/${packagePrefix.replace('.', '/')}/(.+)")
  fun result(impact: Impact, reason: String) = Classification(path, impact, reason)
  val fileName = path.substringAfterLast('/')
  val rootFile = !path.contains('/')

  return when {
    path in selectorWorkflows -> result(All, "selector workflow")
    path.startsWith(".github/") -> result(None, ".github: workflows and repository config")
    path.startsWith("src/main/resources/") || path.startsWith("src/test/resources/") -> result(All, "resources")
    path.startsWith("helm_deploy/") -> result(None, "helm_deploy: deployment config")
    path.startsWith("doc/") || path.endsWith(".md") -> result(None, "documentation")
    path in noneFiles || (rootFile && (fileName.startsWith("docker-compose") || applicationInsights.matches(fileName))) -> result(None, "not used by the tests")
    path.endsWith(".sh") || path.endsWith(".bash") -> result(None, "script")
    path in allFiles || path.startsWith("gradle/") || path.startsWith("gradlew") || path.startsWith("build-logic/") -> result(All, "build configuration")
    else -> classifySource(path, sourceRoot, models) ?: classifySpec(path, models) ?: result(Unclassified, "unclassified, runs everything")
  }
}

private fun classifySource(path: String, sourceRoot: Regex, models: List<ModelConfiguration>): Classification? {
  val relative = sourceRoot.matchEntire(path)?.groupValues?.get(1) ?: return null
  val pkg = relative.substringBefore('/')
  return when {
    !relative.contains('/') -> Classification(path, All, "application root class")
    pkg in sharedPackages -> Classification(path, All, "shared package $pkg")
    else -> {
      val task = models.firstOrNull { it.testPackageName == pkg }?.toTestTaskName() ?: "test"
      Classification(path, Feature(task), "feature package $pkg")
    }
  }
}

private fun classifySpec(path: String, models: List<ModelConfiguration>): Classification? {
  val name = (specRegex.matchEntire(path) ?: ignoreRegex.matchEntire(path))?.groupValues?.get(1) ?: return null
  val model = models.firstOrNull { it.name == name }
  return when {
    name in sharedSpecs -> Classification(path, All, "shared spec $name")
    model == null -> Classification(path, None, "spec $name has no model")
    else -> Classification(path, Feature(if (model.testPackageName != null) model.toTestTaskName() else "test"), "spec $name")
  }
}
