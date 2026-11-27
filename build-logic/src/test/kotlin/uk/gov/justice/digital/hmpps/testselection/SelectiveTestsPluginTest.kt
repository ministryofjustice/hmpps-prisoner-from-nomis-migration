package uk.gov.justice.digital.hmpps.testselection

import org.assertj.core.api.Assertions.assertThat
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream

class SelectiveTestsPluginTest {
  @TempDir
  lateinit var dir: File
  private lateinit var project: Project
  private lateinit var task: SelectTestsTask

  private val base = "src/main/kotlin/uk/gov/justice/digital/hmpps/prisonerfromnomismigration"
  private val allMatrix = """{"tests":["test","testAgencyRegisters"]}"""

  @BeforeEach
  fun setUp() {
    project = ProjectBuilder.builder().withProjectDir(dir).build()
    project.pluginManager.apply(SelectiveTestsPlugin::class.java)
    task = project.tasks.getByName("selectTests") as SelectTestsTask
    project.extensions.getByType(SelectiveTestsExtension::class.java).apply {
      modelConfigurations.set(
        listOf(ModelConfiguration(name = "agency-registers", packageName = "agency", testPackageName = "agency", url = "http://localhost")),
      )
      packagePrefix.set("uk.gov.justice.digital.hmpps.prisonerfromnomismigration")
    }
  }

  private fun changedFiles(vararg paths: String) = File(dir, "changed.txt").apply { writeText(paths.joinToString("\n")) }.path

  private fun run(): String {
    val out = ByteArrayOutputStream()
    val original = System.out
    System.setOut(PrintStream(out))
    try {
      task.print()
    } finally {
      System.setOut(original)
    }
    return out.toString().trim()
  }

  @Test
  fun `inputs are derived lazily from the extension`() {
    assertThat(task.models.get().map { it.name }).containsExactly("agency-registers")
    assertThat(task.group).isEqualTo("Test selection")
  }

  @Test
  fun `mode defaults to off so local runs print every suite`() {
    assertThat(task.mode.get()).isEqualTo(SelectionMode.OFF)
    assertThat(run()).isEqualTo(allMatrix)
  }

  @Test
  fun `off ignores the diff and writes no summary`() {
    task.changedFiles.set(changedFiles("README.md"))
    task.summaryFile.set("summary.md")

    assertThat(run()).isEqualTo(allMatrix)
    assertThat(File(dir, "summary.md")).doesNotExist()
  }

  @Test
  fun `shadow prints every suite and writes the would run summary`() {
    task.mode.set(SelectionMode.SHADOW)
    task.changedFiles.set(changedFiles("$base/agency/A.kt"))
    task.summaryFile.set("summary.md")

    assertThat(run()).isEqualTo(allMatrix)
    assertThat(File(dir, "summary.md").readText()).contains("1 of 2", "`testAgencyRegisters`")
  }

  @Test
  fun `shadow without a summary file only prints the matrix`() {
    task.mode.set(SelectionMode.SHADOW)
    task.changedFiles.set(changedFiles("README.md"))

    assertThat(run()).isEqualTo(allMatrix)
  }

  @Test
  fun `on prints only the selected suites`() {
    task.mode.set(SelectionMode.ON)
    task.changedFiles.set(changedFiles("$base/agency/A.kt"))

    assertThat(run()).isEqualTo("""{"tests":["testAgencyRegisters"]}""")
  }

  @Test
  fun `on prints no suites when nothing relevant changed`() {
    task.mode.set(SelectionMode.ON)
    task.changedFiles.set(changedFiles("README.md"))

    assertThat(run()).isEqualTo("""{"tests":[]}""")
  }

  @Test
  fun `on without changed files or with run all runs everything`() {
    task.mode.set(SelectionMode.ON)
    assertThat(run()).isEqualTo(allMatrix)

    task.changedFiles.set(changedFiles("README.md"))
    task.runAll.set(true)
    assertThat(run()).isEqualTo(allMatrix)
  }

  @Test
  fun `explainTestSelection prints the summary for the supplied files`() {
    val explain = project.tasks.getByName("explainTestSelection") as ExplainTestSelectionTask
    explain.changedFilesProvider = { listOf("$base/agency/A.kt", "README.md") }

    val out = ByteArrayOutputStream()
    val original = System.out
    System.setOut(PrintStream(out))
    try {
      explain.print()
    } finally {
      System.setOut(original)
    }

    assertThat(out.toString()).contains("would run 1 of 2", "`testAgencyRegisters`", "README.md")
  }
}
