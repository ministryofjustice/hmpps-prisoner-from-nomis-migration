package uk.gov.justice.digital.hmpps.testselection

import java.io.File

// Committed, uncommitted and untracked changes since the branch left `base`. Renames are reported as a delete and an add.
fun changedFilesSince(base: String, dir: File): List<String> {
  val mergeBase = git(dir, "merge-base", base, "HEAD").trim()
  return (git(dir, "diff", "--name-only", "--no-renames", mergeBase) + git(dir, "ls-files", "--others", "--exclude-standard"))
    .lines().filter { it.isNotBlank() }.distinct()
}

private fun git(dir: File, vararg args: String): String {
  val process = ProcessBuilder(listOf("git") + args).directory(dir).redirectErrorStream(true).start()
  val output = process.inputStream.bufferedReader().readText()
  check(process.waitFor() == 0) { "git ${args.joinToString(" ")} failed: $output" }
  return output
}
