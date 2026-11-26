package uk.gov.justice.digital.hmpps.testselection

import org.assertj.core.api.Assertions.assertThat
import org.gradle.api.Project
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import org.gradle.api.tasks.testing.Test as TestTask

class ModelTasksTest {
  private val project: Project = ProjectBuilder.builder().build()

  private fun model(name: String, testPackageName: String? = null, hasProductionVersion: Boolean = true) = ModelConfiguration(
    name = name,
    packageName = name.replace("-", "."),
    testPackageName = testPackageName,
    url = "http://localhost/$name",
    models = "ModelA,ModelB",
    hasProductionVersion = hasProductionVersion,
  )

  private fun evaluate(vararg models: ModelConfiguration) = with(project) {
    pluginManager.apply("java")
    pluginManager.apply("org.openapi.generator")
    pluginManager.apply(SelectiveTestsPlugin::class.java)
    extensions.configure(SelectiveTestsExtension::class.java) {
      modelConfigurations.set(models.toList())
      packagePrefix.set("com.example")
    }
    (this as ProjectInternal).evaluate()
  }

  @Test
  fun `registers generate, write and read tasks for each model`() {
    evaluate(model("alerts"), model("core-person"))

    listOf("Alerts" to "alerts", "CorePerson" to "core-person").forEach { (camel, name) ->
      with(project.tasks.getByName("build${camel}ApiModel")) {
        assertThat(this).isInstanceOf(GenerateTask::class.java)
        assertThat(group).isEqualTo("Generate model from API JSON definition")
        assertThat(description).isEqualTo("Generate model from API JSON definition for $name")
      }
      with(project.tasks.getByName("write${camel}Json")) {
        assertThat(this).isInstanceOf(WriteJsonTask::class.java)
        assertThat(group).isEqualTo("Write JSON")
        assertThat(description).isEqualTo("Write JSON for $name")
      }
      with(project.tasks.getByName("read${camel}ProductionVersion")) {
        assertThat(this).isInstanceOf(ReadProductionVersionTask::class.java)
        assertThat(group).isEqualTo("Read current production version")
        assertThat(description).isEqualTo("Read current production version for $name")
      }
    }
  }

  @Test
  fun `read task prints no production version when the model has none`() {
    evaluate(model("staff", hasProductionVersion = false))
    val task = project.tasks.getByName("readStaffProductionVersion")
    assertThat(task).isNotInstanceOf(ReadProductionVersionTask::class.java)

    val output = ByteArrayOutputStream()
    val originalOut = System.out
    System.setOut(PrintStream(output))
    try {
      task.actions.forEach { it.execute(task) }
    } finally {
      System.setOut(originalOut)
    }
    assertThat(output.toString().trim()).isEqualTo("no production version")
  }

  @Test
  fun `only models with a test package get a test task which check depends on`() {
    evaluate(model("alerts", testPackageName = "alertstests"), model("nomis-prisoner"))

    val testAlerts = project.tasks.getByName("testAlerts") as TestTask
    assertThat(testAlerts.group).isEqualTo("Run tests")
    assertThat(testAlerts.filter.includePatterns).containsExactly("com.example.alertstests.*")
    assertThat(project.tasks.names).doesNotContain("testNomisPrisoner")
    assertThat(project.tasks.getByName("check").taskDependencies.getDependencies(null)).contains(testAlerts)
  }

  @Test
  fun `test excludes the separate test packages`() {
    evaluate(model("alerts", testPackageName = "alerts"), model("nomis-prisoner"), model("visit-balance", testPackageName = "visitbalances"))

    val test = project.tasks.getByName("test") as TestTask
    assertThat(test.filter.excludePatterns).containsExactlyInAnyOrder("com.example.alerts.*", "com.example.visitbalances.*")
  }

  @Test
  fun `generate tasks use the package prefix, run one at a time and honour ignore file overrides`() {
    project.file("openapi-generator-ignore-visit-balance").writeText("")
    evaluate(model("alerts"), model("visit-balance"))

    val alerts = project.tasks.getByName("buildAlertsApiModel") as GenerateTask
    val visitBalance = project.tasks.getByName("buildVisitBalanceApiModel") as GenerateTask

    assertThat(visitBalance.modelPackage.get()).isEqualTo("com.example.visit.balance.model")
    assertThat(visitBalance.apiPackage.get()).isEqualTo("com.example.visit.balance.api")
    assertThat(alerts.mustRunAfter.getDependencies(alerts)).containsExactly(project.tasks.getByName("writeAlertsJson"))
    assertThat(visitBalance.mustRunAfter.getDependencies(visitBalance))
      .containsExactlyInAnyOrder(project.tasks.getByName("writeVisitBalanceJson"), alerts)

    assertThat(alerts.globalProperties.get()).isEqualTo(mapOf("models" to "ModelA,ModelB"))
    assertThat(alerts.ignoreFileOverride.isPresent).isFalse()
    assertThat(visitBalance.ignoreFileOverride.get().asFile).isEqualTo(project.file("openapi-generator-ignore-visit-balance"))
    assertThat(visitBalance.globalProperties.get()).isEmpty()
  }
}
