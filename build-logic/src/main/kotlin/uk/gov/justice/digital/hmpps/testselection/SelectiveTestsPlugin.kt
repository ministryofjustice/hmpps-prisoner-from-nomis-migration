package uk.gov.justice.digital.hmpps.testselection

import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.ProjectLayout
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import javax.inject.Inject

abstract class SelectiveTestsExtension {
  abstract val modelConfigurations: ListProperty<ModelConfiguration>
  abstract val packagePrefix: Property<String>
}

abstract class SelectTestsTask : DefaultTask() {
  @get:Input
  abstract val models: ListProperty<ModelConfiguration>

  @get:Input
  abstract val packagePrefix: Property<String>

  @get:Input
  @get:Optional
  @get:Option(option = "changed-files", description = "File of newline-separated changed paths")
  abstract val changedFiles: Property<String>

  @get:Input
  @get:Option(option = "run-all", description = "Run every suite regardless of the changed files")
  abstract val runAll: Property<Boolean>

  @get:Input
  @get:Option(option = "mode", description = "off prints every suite, shadow also writes the would-run summary, on prints the selected suites")
  abstract val mode: Property<SelectionMode>

  @get:Input
  @get:Optional
  @get:Option(option = "summary-file", description = "Where shadow mode writes the markdown summary")
  abstract val summaryFile: Property<String>

  @get:Inject
  abstract val layout: ProjectLayout

  init {
    runAll.convention(false)
    mode.convention(SelectionMode.OFF)
  }

  @TaskAction
  fun print() {
    val changed = changedFiles.orNull?.let { layout.projectDirectory.file(it).asFile.readLines() }.orEmpty()
    val plan = select(changed, runAll.get(), models.get(), packagePrefix.get())
    val suites = if (mode.get() == SelectionMode.ON) plan.suites else plan.allSuites
    if (mode.get() == SelectionMode.SHADOW) {
      summaryFile.orNull?.let { layout.projectDirectory.file(it).asFile.writeText(plan.toMarkdown()) }
    }
    println(suites.toGithubMatrix())
  }
}

abstract class ExplainTestSelectionTask : DefaultTask() {
  @get:Input
  abstract val models: ListProperty<ModelConfiguration>

  @get:Input
  abstract val packagePrefix: Property<String>

  @get:Input
  @get:Option(option = "base", description = "Branch to compare against, defaults to origin/main")
  abstract val base: Property<String>

  @get:Inject
  abstract val layout: ProjectLayout

  // lets tests supply the files instead of running git
  @get:Internal
  var changedFilesProvider: (() -> List<String>)? = null

  init {
    base.convention("origin/main")
  }

  @TaskAction
  fun print() {
    val changed = changedFilesProvider?.invoke() ?: changedFilesSince(base.get(), layout.projectDirectory.asFile)
    println(select(changed, false, models.get(), packagePrefix.get()).toMarkdown())
  }
}

class SelectiveTestsPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    val extension = project.extensions.create("selectiveTests", SelectiveTestsExtension::class.java)
    project.tasks.register("selectTests", SelectTestsTask::class.java) {
      group = "Test selection"
      description = "Prints the test tasks to run as a GitHub matrix"
      models.set(extension.modelConfigurations)
      packagePrefix.set(extension.packagePrefix)
    }
    project.tasks.register("explainTestSelection", ExplainTestSelectionTask::class.java) {
      group = "Test selection"
      description = "Prints which test suites the changes on this branch (against origin/main) would need"
      models.set(extension.modelConfigurations)
      packagePrefix.set(extension.packagePrefix)
    }
    // the tasks to register depend on the models, which are only known once the build script has been evaluated
    project.afterEvaluate {
      registerModelTasks(extension.modelConfigurations.get(), extension.packagePrefix.get())
    }
  }
}
