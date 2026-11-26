package uk.gov.justice.digital.hmpps.testselection

import org.assertj.core.api.Assertions.assertThat
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test

class SelectiveTestsPluginTest {
  @Test
  fun `selectTests input is derived lazily from the extension`() {
    val project = ProjectBuilder.builder().build()
    project.pluginManager.apply(SelectiveTestsPlugin::class.java)
    val task = project.tasks.getByName("selectTests") as SelectTestsTask

    project.extensions.getByType(SelectiveTestsExtension::class.java).modelConfigurations.set(
      listOf(ModelConfiguration(name = "agency-registers", packageName = "agency", testPackageName = "agency", url = "http://localhost")),
    )

    assertThat(task.tests.get()).containsExactly("test", "testAgencyRegisters")
    assertThat(task.group).isEqualTo("Test selection")
  }
}
