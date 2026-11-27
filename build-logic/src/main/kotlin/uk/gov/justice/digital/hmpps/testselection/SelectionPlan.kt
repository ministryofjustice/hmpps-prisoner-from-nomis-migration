package uk.gov.justice.digital.hmpps.testselection

import uk.gov.justice.digital.hmpps.testselection.Impact.Feature
import uk.gov.justice.digital.hmpps.testselection.Impact.None

enum class SelectionMode { OFF, SHADOW, ON }

data class SelectionPlan(
  val suites: List<String>,
  val allSuites: List<String>,
  val files: List<Classification>,
  val reason: String?,
) {
  fun toMarkdown(maxRows: Int = 100): String = buildString {
    appendLine("## Test selection (would run ${suites.size} of ${allSuites.size} suites)")
    appendLine()
    appendLine(if (suites.isEmpty()) "No suites needed." else suites.joinToString(", ") { "`$it`" })
    reason?.let { appendLine().appendLine("Everything runs: $it") }
    if (files.isNotEmpty()) {
      appendLine()
      appendLine("| File | Rule | Suites |")
      appendLine("| --- | --- | --- |")
      files.take(maxRows).forEach { appendLine("| ${it.path.escape()} | ${it.reason.escape()} | ${it.impact.describe()} |") }
      if (files.size > maxRows) appendLine().appendLine("... and ${files.size - maxRows} more files")
    }
  }

  // file names come from the pull request, so keep them from being rendered as markdown or html
  private fun String.escape() = replace("<", "&lt;").replace(">", "&gt;").replace(Regex("""[\\|`\[\]()]""")) { "\\${it.value}" }

  private fun Impact.describe() = when (this) {
    None -> "none"
    is Feature -> "`$testTask`"
    else -> "all"
  }
}

fun select(changedFiles: List<String>, runAll: Boolean, models: List<ModelConfiguration>, packagePrefix: String): SelectionPlan {
  val allSuites = selectTests(models)
  val files = changedFiles.filter { it.isNotBlank() }.map { classify(it.trim(), models, packagePrefix) }
  val impacts = files.map { it.impact }
  val everything = when {
    runAll -> "run-all requested"
    files.isEmpty() -> "no changed files"
    impacts.any { it !is None && it !is Feature } -> "a changed file affects every suite"
    else -> null
  }
  val suites = if (everything != null) {
    allSuites
  } else {
    val tasks = impacts.filterIsInstance<Feature>().map { it.testTask }.toSet()
    allSuites.filter { it in tasks }
  }
  return SelectionPlan(suites, allSuites, files, everything)
}
