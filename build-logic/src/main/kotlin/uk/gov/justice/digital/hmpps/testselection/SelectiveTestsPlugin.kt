package uk.gov.justice.digital.hmpps.testselection

import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

abstract class SelectiveTestsExtension {
  abstract val modelConfigurations: ListProperty<ModelConfiguration>
  abstract val packagePrefix: Property<String>
}

abstract class SelectTestsTask : DefaultTask() {
  @get:Input
  abstract val tests: ListProperty<String>

  @TaskAction
  fun print() {
    println(tests.get().toGithubMatrix())
  }
}

class SelectiveTestsPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    val extension = project.extensions.create("selectiveTests", SelectiveTestsExtension::class.java)
    project.tasks.register("selectTests", SelectTestsTask::class.java) {
      group = "Test selection"
      description = "Prints the test tasks to run as a GitHub matrix"
      tests.set(extension.modelConfigurations.map { selectTests(it) })
    }
    // the tasks to register depend on the models, which are only known once the build script has been evaluated
    project.afterEvaluate {
      registerModelTasks(extension.modelConfigurations.get(), extension.packagePrefix.get())
    }
  }
}
