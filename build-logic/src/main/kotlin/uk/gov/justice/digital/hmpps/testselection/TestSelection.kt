package uk.gov.justice.digital.hmpps.testselection

fun selectTests(models: List<ModelConfiguration>): List<String> {
  val modelTests = models.filter { it.testPackageName != null }.map { it.toTestTaskName() }
  val duplicates = modelTests.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
  require(duplicates.isEmpty()) { "Duplicate test task names: ${duplicates.sorted()}" }
  return listOf("test") + modelTests.sorted()
}

fun List<String>.toGithubMatrix(): String = joinToString(",", """{"tests":[""", "]}") { "\"$it\"" }
