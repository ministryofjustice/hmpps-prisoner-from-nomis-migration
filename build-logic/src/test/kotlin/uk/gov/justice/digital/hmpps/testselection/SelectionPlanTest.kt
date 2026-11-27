package uk.gov.justice.digital.hmpps.testselection

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SelectionPlanTest {
  private val prefix = "uk.gov.justice.digital.hmpps.prisonerfromnomismigration"
  private val base = "src/main/kotlin/uk/gov/justice/digital/hmpps/prisonerfromnomismigration"
  private val models = listOf(
    ModelConfiguration(name = "alerts", packageName = "alerts", testPackageName = "alerts", url = "http://localhost"),
    ModelConfiguration(name = "casenotes", packageName = "casenotes", testPackageName = "casenotes", url = "http://localhost"),
    ModelConfiguration(name = "agency-registers", packageName = "agencyregisters", testPackageName = "agency", url = "http://localhost"),
  )
  private val allSuites = listOf("test", "testAgencyRegisters", "testAlerts", "testCasenotes")

  private fun plan(vararg files: String, runAll: Boolean = false) = select(files.toList(), runAll, models, prefix)

  @Test
  fun `features are unioned and sorted with test first`() {
    val result = plan("$base/casenotes/A.kt", "$base/alerts/A.kt", "$base/health/A.kt", "$base/alerts/B.kt")

    assertThat(result.suites).containsExactly("test", "testAlerts", "testCasenotes")
    assertThat(result.allSuites).isEqualTo(allSuites)
  }

  @Test
  fun `no suites when nothing can affect the tests`() {
    assertThat(plan("README.md", "helm_deploy/values-dev.yaml").suites).isEmpty()
  }

  @Test
  fun `a shared file runs everything even alongside features`() {
    assertThat(plan("$base/alerts/A.kt", "$base/service/S.kt").suites).isEqualTo(allSuites)
  }

  @Test
  fun `an unclassified file runs everything`() {
    assertThat(plan("$base/alerts/A.kt", "mystery.txt").suites).isEqualTo(allSuites)
  }

  @Test
  fun `an empty diff runs everything`() {
    assertThat(plan().suites).isEqualTo(allSuites)
  }

  @Test
  fun `run all runs everything even when files are irrelevant`() {
    assertThat(plan("README.md", runAll = true).suites).isEqualTo(allSuites)
  }

  @Test
  fun `summary lists suites and a row per file`() {
    val markdown = plan("$base/alerts/A.kt", "README.md").toMarkdown()

    assertThat(markdown).contains("1 of 4")
    assertThat(markdown).contains("`testAlerts`")
    assertThat(markdown).contains("| $base/alerts/A.kt |", "| README.md |")
  }

  @Test
  fun `summary escapes markdown in file names`() {
    val markdown = plan("docs/a|b <img src=x> [link](http://x) `code`.md").toMarkdown()

    assertThat(markdown).contains("docs/a\\|b &lt;img src=x&gt; \\[link\\]\\(http://x\\) \\`code\\`.md")
    assertThat(markdown).doesNotContain("<img")
  }

  @Test
  fun `summary explains why everything runs`() {
    assertThat(plan(runAll = true).toMarkdown()).contains("4 of 4", "run-all")
    assertThat(plan().toMarkdown()).contains("4 of 4", "no changed files")
  }

  @Test
  fun `summary caps long file lists`() {
    val files = (1..60).map { "doc/$it.md" }.toTypedArray()

    val markdown = plan(*files).toMarkdown(maxRows = 50)

    assertThat(markdown).contains("| doc/50.md |").doesNotContain("| doc/51.md |").contains("10 more")
  }
}
