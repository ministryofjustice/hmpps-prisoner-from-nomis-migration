package uk.gov.justice.digital.hmpps.testselection

fun selectTests(models: List<ModelConfiguration>): List<String> = listOf("test") + models.filter { it.testPackageName != null }.map { it.toTestTaskName() }.sorted()

fun List<String>.toGithubMatrix(): String = joinToString(",", """{"tests":[""", "]}") { "\"$it\"" }
