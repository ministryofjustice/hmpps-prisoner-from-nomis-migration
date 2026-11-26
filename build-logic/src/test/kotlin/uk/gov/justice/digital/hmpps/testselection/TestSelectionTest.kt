package uk.gov.justice.digital.hmpps.testselection

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TestSelectionTest {
  private fun model(name: String, testPackageName: String? = null) = ModelConfiguration(name = name, packageName = name, testPackageName = testPackageName, url = "http://localhost")

  @Test
  fun `test task comes first then models with a test package in alphabetical order`() {
    val tests = selectTests(listOf(model("b", "b"), model("none"), model("a-b-c", "abc")))

    assertThat(tests).containsExactly("test", "testABC", "testB")
  }

  @Test
  fun `only the default test task when no model has separate tests`() {
    assertThat(selectTests(listOf(model("none")))).containsExactly("test")
  }

  @Test
  fun `task name is derived from model name not test package`() {
    val tests = selectTests(models)

    assertThat(tests).contains("testAgencyRegisters", "testSentencingAdjustments", "testVisitBalance")
    assertThat(tests).doesNotContain("testAgency", "testSentencing", "testVisitbalances")
  }

  @Test
  fun `matrix is single line json`() {
    assertThat(listOf("test", "testA").toGithubMatrix()).isEqualTo("""{"tests":["test","testA"]}""")
  }

  @Test
  fun `all configured suites are selected without duplicates`() {
    val tests = selectTests(models)

    assertThat(tests).isNotEmpty().doesNotHaveDuplicates()
  }
}
