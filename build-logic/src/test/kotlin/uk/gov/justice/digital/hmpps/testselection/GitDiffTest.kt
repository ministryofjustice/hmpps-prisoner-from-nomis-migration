package uk.gov.justice.digital.hmpps.testselection

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class GitDiffTest {
  @TempDir
  lateinit var dir: File

  private fun git(vararg args: String) {
    val process = ProcessBuilder(listOf("git", "-c", "user.name=t", "-c", "user.email=t@t") + args).directory(dir).redirectErrorStream(true).start()
    check(process.waitFor() == 0) { process.inputStream.readText() }
  }

  private fun write(path: String, text: String = path) = File(dir, path).apply { parentFile.mkdirs() }.writeText(text)

  @BeforeEach
  fun setUp() {
    git("init", "-q", "-b", "main")
    write("kept.txt")
    write("old/Moved.kt", "a long enough body to be detected as a rename\n".repeat(5))
    git("add", ".")
    git("commit", "-q", "-m", "base")
    git("checkout", "-q", "-b", "feature")
  }

  @Test
  fun `lists committed, uncommitted and untracked changes since the merge base`() {
    write("committed.txt")
    git("add", ".")
    git("commit", "-q", "-m", "committed")
    write("kept.txt", "changed")
    write("untracked.txt")

    assertThat(changedFilesSince("main", dir)).containsExactlyInAnyOrder("committed.txt", "kept.txt", "untracked.txt")
  }

  @Test
  fun `ignores changes made on the base branch after branching`() {
    git("checkout", "-q", "main")
    write("on-main.txt")
    git("add", ".")
    git("commit", "-q", "-m", "main moved on")
    git("checkout", "-q", "feature")

    assertThat(changedFilesSince("main", dir)).isEmpty()
  }

  @Test
  fun `a rename reports both the old and the new path`() {
    git("mv", "old/Moved.kt", "new/Moved.kt")
    git("commit", "-q", "-m", "move")

    assertThat(changedFilesSince("main", dir)).containsExactlyInAnyOrder("old/Moved.kt", "new/Moved.kt")
  }
}
