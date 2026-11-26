package uk.gov.justice.digital.hmpps.testselection

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.plugins.jvm.JvmTestSuite
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.testing.Test
import org.gradle.testing.base.TestingExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import org.jlleitschuh.gradle.ktlint.tasks.KtLintCheckTask
import org.jlleitschuh.gradle.ktlint.tasks.KtLintFormatTask
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask
import tools.jackson.databind.json.JsonMapper
import java.net.URI

@CacheableTask
abstract class WriteJsonTask : DefaultTask() {
  private companion object {
    private val mapper = JsonMapper()
  }

  @get:Input
  abstract val url: Property<String>

  @get:OutputFile
  abstract val outputFile: RegularFileProperty

  @TaskAction
  fun run() {
    val json = URI.create(url.get()).toURL().readText()
    val formattedJson = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(mapper.readTree(json))
    outputFile.get().asFile.writeText(formattedJson)
    logger.lifecycle("Written ${outputFile.get()} from ${url.get()}")
  }
}

@CacheableTask
abstract class ReadProductionVersionTask : DefaultTask() {
  private companion object {
    private val mapper = JsonMapper()
  }

  @get:Input
  abstract val url: Property<String>

  @TaskAction
  fun run() {
    val productionUrl = url.get().replace("-dev".toRegex(), "")
      .replace("dev.".toRegex(), "")
      .replace("/v3/api-docs".toRegex(), "/info")
    val json = URI.create(productionUrl).toURL().readText()
    val version = mapper.readTree(json).at("/build/version").asString()
    println(version)
  }
}

private val generatorConfigOptions = mapOf(
  "dateLibrary" to "java8-localdatetime",
  "serializationLibrary" to "jackson",
  "enumPropertyNaming" to "original",
  "useSpringBoot3" to "true",
)

internal fun Project.registerModelTasks(models: List<ModelConfiguration>, packagePrefix: String) {
  val buildModelTaskNames = models.map { it.toBuildModelTaskName() }
  models.forEach { registerWriteJsonTask(it) }
  models.forEach { registerReadProductionVersionTask(it) }

  pluginManager.withPlugin("org.openapi.generator") {
    models.forEachIndexed { i, model ->
      // GenerateTask is not safe to run in parallel so need to ensure that only one runs at once
      registerBuildModelTask(model, packagePrefix, previous = models.getOrNull(i - 1))
    }
  }
  pluginManager.withPlugin("java") {
    registerTestTasks(models, packagePrefix)
  }
  pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
    tasks.withType(KotlinCompile::class.java).configureEach { dependsOn(buildModelTaskNames) }
    extensions.getByType(KotlinJvmProjectExtension::class.java).sourceSets.getByName("main").kotlin {
      models.forEach { srcDir(layout.buildDirectory.dir("generated/${it.output}/src/main/kotlin")) }
    }
  }
  pluginManager.withPlugin("org.jlleitschuh.gradle.ktlint") {
    tasks.withType(KtLintCheckTask::class.java).configureEach { mustRunAfter(buildModelTaskNames) }
    tasks.withType(KtLintFormatTask::class.java).configureEach { mustRunAfter(buildModelTaskNames) }
    val generatedDirs = models.map { layout.buildDirectory.dir("generated/${it.output}/src/main/").get().toString() }
    extensions.getByType(KtlintExtension::class.java).filter {
      exclude { element -> generatedDirs.any { element.file.path.contains(it) } }
    }
  }
}

private fun Project.registerWriteJsonTask(model: ModelConfiguration) {
  tasks.register(model.toWriteJsonTaskName(), WriteJsonTask::class.java) {
    group = "Write JSON"
    description = "Write JSON for ${model.name}"
    url.set(model.url)
    outputFile.set(file(model.input(projectDir)))
    // ensure that the write task happens every time
    outputs.upToDateWhen { false }
  }
}

private fun Project.registerReadProductionVersionTask(model: ModelConfiguration) {
  val name = model.toReadProductionVersionTaskName()
  val task = if (model.hasProductionVersion) {
    tasks.register(name, ReadProductionVersionTask::class.java) { url.set(model.url) }
  } else {
    tasks.register(name) { doLast { println("no production version") } }
  }
  task.configure {
    group = "Read current production version"
    description = "Read current production version for ${model.name}"
  }
}

private fun Project.registerBuildModelTask(model: ModelConfiguration, packagePrefix: String, previous: ModelConfiguration?) {
  tasks.register(model.toBuildModelTaskName(), GenerateTask::class.java) {
    group = "Generate model from API JSON definition"
    description = "Generate model from API JSON definition for ${model.name}"
    generatorName.set("kotlin")
    library.set("jvm-spring-webclient")
    skipValidateSpec.set(true)
    inputSpec.set(model.input(projectDir))
    outputDir.set(layout.buildDirectory.dir("generated/${model.output}").get().asFile.path)
    cleanupOutput.set(true)
    modelPackage.set("$packagePrefix.${model.packageName}.model")
    apiPackage.set("$packagePrefix.${model.packageName}.api")
    configOptions.set(generatorConfigOptions)
    val ignoreFile = file("openapi-generator-ignore-${model.name}")
    if (ignoreFile.exists()) {
      ignoreFileOverride.set(ignoreFile)
    } else {
      globalProperties.set(mapOf("models" to model.models))
    }
    generateModelTests.set(false)
    generateModelDocumentation.set(false)
    mustRunAfter(model.toWriteJsonTaskName())
    previous?.let { mustRunAfter(it.toBuildModelTaskName()) }
  }
}

private fun Project.registerTestTasks(models: List<ModelConfiguration>, packagePrefix: String) {
  val separateTestPackages = models.mapNotNull { model -> model.testPackageName?.let { model to it } }
  val testSuite = extensions.getByType(TestingExtension::class.java).suites.named("test", JvmTestSuite::class.java)

  separateTestPackages.forEach { (model, testPackageName) ->
    val task = tasks.register(model.toTestTaskName(), Test::class.java) {
      testClassesDirs = files(testSuite.map { it.sources.output.classesDirs })
      classpath = files(testSuite.map { it.sources.runtimeClasspath })
      group = "Run tests"
      description = "Run tests for ${model.name}"
      shouldRunAfter("test")
      useJUnitPlatform()
      filter { includeTestsMatching("$packagePrefix.$testPackageName.*") }
      maxHeapSize = "1024m"
    }
    tasks.named("check") { dependsOn(task) }
  }

  tasks.named("test", Test::class.java) {
    filter {
      separateTestPackages.forEach { (_, testPackageName) -> excludeTestsMatching("$packagePrefix.$testPackageName.*") }
    }
  }
}
