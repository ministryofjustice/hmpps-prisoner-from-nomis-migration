package uk.gov.justice.digital.hmpps.testselection

import org.gradle.api.Plugin
import org.gradle.api.Project

class SelectiveTestsPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    val matrix = selectTests(models).toGithubMatrix()
    project.tasks.register("selectTests") {
      group = "Test selection"
      description = "Prints the test tasks to run as a GitHub matrix"
      doLast { println(matrix) }
    }
  }
}
