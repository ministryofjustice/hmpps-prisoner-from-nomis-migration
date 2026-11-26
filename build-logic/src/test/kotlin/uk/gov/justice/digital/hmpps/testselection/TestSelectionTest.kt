package uk.gov.justice.digital.hmpps.testselection

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
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
    val tests = selectTests(
      listOf(
        model("agency-registers", "agency"),
        model("sentencing-adjustments", "sentencing"),
        model("visit-balance", "visitbalances"),
      ),
    )

    assertThat(tests).containsExactly("test", "testAgencyRegisters", "testSentencingAdjustments", "testVisitBalance")
  }

  @Test
  fun `duplicate task names are rejected`() {
    assertThatThrownBy { selectTests(listOf(model("a-b", "x"), model("aB", "y"), model("c", "c"))) }
      .isInstanceOf(IllegalArgumentException::class.java)
      .hasMessageContaining("testAB")
      .hasMessageNotContaining("testC")
  }

  @Test
  fun `matrix is single line json`() {
    assertThat(listOf("test", "testA").toGithubMatrix()).isEqualTo("""{"tests":["test","testA"]}""")
  }
}
