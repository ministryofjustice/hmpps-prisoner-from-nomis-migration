package uk.gov.justice.digital.hmpps.testselection

import java.io.File
import java.io.Serializable

data class ModelConfiguration(val name: String, val packageName: String, val testPackageName: String? = null, val url: String, val models: String = "", val hasProductionVersion: Boolean = true) : Serializable {
  fun toBuildModelTaskName(): String = "build${nameToCamel()}ApiModel"
  fun toWriteJsonTaskName(): String = "write${nameToCamel()}Json"
  fun toReadProductionVersionTaskName(): String = "read${nameToCamel()}ProductionVersion"
  fun toTestTaskName(): String = "test${nameToCamel()}"
  private val snakeRegex = "-[a-zA-Z]".toRegex()
  private fun nameToCamel(): String = snakeRegex.replace(name) {
    it.value.replace("-", "").uppercase()
  }.replaceFirstChar { it.uppercase() }

  fun input(projectDir: File): String = "$projectDir/openapi-specs/$name-api-docs.json"
  val output: String
    get() = name
}
