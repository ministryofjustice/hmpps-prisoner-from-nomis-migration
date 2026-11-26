@file:Suppress("UnstableApiUsage")

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jlleitschuh.gradle.ktlint.tasks.KtLintCheckTask
import org.jlleitschuh.gradle.ktlint.tasks.KtLintFormatTask
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.testselection.models
import uk.gov.justice.digital.hmpps.testselection.packagePrefix
import java.net.URI
import kotlin.io.path.exists
import kotlin.io.path.pathString
import kotlin.io.path.Path as KotlinPath

plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "11.1.1"
  kotlin("plugin.spring") version "2.4.21"
  id("org.openapi.generator") version "7.27.0"
  id("uk.gov.justice.digital.hmpps.selective-tests")
}

configurations {
  implementation {
    exclude(module = "spring-boot-starter-web")
    exclude(module = "spring-boot-starter-tomcat")
  }
}

dependencies {
  implementation("uk.gov.justice.service.hmpps:hmpps-kotlin-spring-boot-starter:3.1.0")
  implementation("uk.gov.justice.service.hmpps:hmpps-sqs-spring-boot-starter:7.6.0")
  implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
  implementation("org.springframework.boot:spring-boot-starter-flyway")
  implementation("org.springframework.boot:spring-boot-starter-security")
  implementation("org.springframework.boot:spring-boot-starter-webclient")
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springframework.security:spring-security-access")
  implementation("org.springdoc:springdoc-openapi-starter-webflux-ui:3.2.0")
  // Temporarily pin swagger-ui for dompurify issues CVE-2026-11680(2-4)
  constraints {
    implementation("org.webjars:swagger-ui:5.34.1")
  }

  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk9")

  implementation("io.opentelemetry:opentelemetry-extension-kotlin:1.65.0")

  runtimeOnly("org.postgresql:r2dbc-postgresql:1.1.3.RELEASE")
  // Pin scram for CVE-2026-53712 whilst waiting for postgresql to upgrade
  constraints {
    implementation("com.ongres.scram:scram-client:3.3")
    implementation("com.ongres.scram:scram-common:3.3")
  }
  runtimeOnly("org.springframework.boot:spring-boot-starter-jdbc")
  runtimeOnly("org.postgresql:postgresql:42.7.15")
  runtimeOnly("org.flywaydb:flyway-database-postgresql")

  testImplementation("uk.gov.justice.service.hmpps:hmpps-kotlin-spring-boot-starter-test:3.1.0")
  testImplementation("org.springframework.boot:spring-boot-starter-data-r2dbc-test")
  testImplementation("org.springframework.boot:spring-boot-starter-webflux-test")
  testImplementation("io.swagger.parser.v3:swagger-parser:2.1.49") {
    exclude(group = "io.swagger.core.v3")
  }
  testImplementation("io.swagger.core.v3:swagger-core-jakarta:2.2.56")

  testImplementation("org.wiremock:wiremock-standalone:3.13.2")
  testImplementation("org.testcontainers:localstack:1.21.4")
  testImplementation("com.amazonaws:aws-java-sdk-core:1.12.797")
  testImplementation("org.awaitility:awaitility-kotlin:4.3.0")
  testImplementation("org.testcontainers:testcontainers-postgresql:2.0.5")
  testImplementation("io.mockk:mockk:1.14.12")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
}

kotlin {
  jvmToolchain(25)
  compilerOptions {
    freeCompilerArgs.add("-Xcollection-literals")
    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25)
  }
}

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
    val formattedJson = mapper.let { mapper ->
      mapper.writerWithDefaultPrettyPrinter().writeValueAsString(mapper.readTree(json))
    }
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

tasks {
  withType<KotlinCompile> {
    dependsOn(models.map { it.toBuildModelTaskName() })
  }
  withType<KtLintCheckTask> {
    mustRunAfter(models.map { it.toBuildModelTaskName() })
  }
  withType<KtLintFormatTask> {
    mustRunAfter(models.map { it.toBuildModelTaskName() })
  }
}
val separateTestPackages = mutableListOf<String>()
models.forEachIndexed { i, model ->
  tasks.register<GenerateTask>(model.toBuildModelTaskName()) {
    val buildDirectory: DirectoryProperty = project.layout.buildDirectory
    group = "Generate model from API JSON definition"
    description = "Generate model from API JSON definition for ${model.name}"
    generatorName.set("kotlin")
    library.set("jvm-spring-webclient")
    skipValidateSpec.set(true)
    inputSpec.set(model.input(projectDir))
    outputDir.set(buildDirectory.dir("generated/${model.output}").get().asFile.path)
    cleanupOutput.set(true)
    modelPackage.set("$packagePrefix.${model.packageName}.model")
    apiPackage.set("$packagePrefix.${model.packageName}.api")
    configOptions.set(configValues)
    KotlinPath("$projectDir/openapi-generator-ignore-${model.name}")
      .takeIf { p -> p.exists() }?.apply { ignoreFileOverride.set(this.pathString) }
      ?: globalProperties.set(mapOf("models" to model.models))
    generateModelTests.set(false)
    generateModelDocumentation.set(false)
    mustRunAfter(model.toWriteJsonTaskName())
    // GenerateTask is not safe to run in parallel so need to ensure that only one runs at once
    if (i != 0) mustRunAfter(models[i - 1].toBuildModelTaskName())
  }
  tasks.register<WriteJsonTask>(model.toWriteJsonTaskName()) {
    val buildDirectory: DirectoryProperty = project.layout.buildDirectory
    group = "Write JSON"
    description = "Write JSON for ${model.name}"
    url.set(model.url)
    outputFile.set(buildDirectory.file(model.input(projectDir)))
    // ensure that the write task happens every time
    outputs.upToDateWhen { false }
  }
  if (model.hasProductionVersion) {
    tasks.register<ReadProductionVersionTask>(model.toReadProductionVersionTaskName()) {
      group = "Read current production version"
      description = "Read current production version for ${model.name}"
      url.set(model.url)
    }
  } else {
    tasks.register(model.toReadProductionVersionTaskName()) {
      group = "Read current production version"
      description = "Read current production version for ${model.name}"
      doLast {
        println("no production version")
      }
    }
  }
  model.testPackageName?.let { testPackageName ->
    separateTestPackages.add(testPackageName)
    val test by testing.suites.existing(JvmTestSuite::class)
    val task = tasks.register<Test>(model.toTestTaskName()) {
      testClassesDirs = files(test.map { it.sources.output.classesDirs })
      classpath = files(test.map { it.sources.runtimeClasspath })
      group = "Run tests"
      description = "Run tests for ${model.name}"
      shouldRunAfter("test")
      useJUnitPlatform()
      filter {
        includeTestsMatching("$packagePrefix.$testPackageName.*")
      }
      maxHeapSize = "1024m"
    }
    tasks.check { dependsOn(task) }
  }
}

tasks.test {
  dependsOn(gradle.includedBuild("build-logic").task(":test"))
  filter {
    separateTestPackages.forEach {
      excludeTestsMatching("$packagePrefix.$it.*")
    }
  }
  maxHeapSize = "1024m"
}

tasks.ktlintCheck {
  dependsOn(gradle.includedBuild("build-logic").task(":ktlintCheck"))
}

val configValues = mapOf(
  "dateLibrary" to "java8-localdatetime",
  "serializationLibrary" to "jackson",
  "enumPropertyNaming" to "original",
  "useSpringBoot3" to "true",
)

kotlin {
  val buildDirectory: DirectoryProperty = project.layout.buildDirectory
  sourceSets["main"].apply {
    models.map { it.output }.forEach { generatedProject ->
      kotlin.srcDir(buildDirectory.dir("generated/$generatedProject/src/main/kotlin").get().toString())
    }
  }
}

ktlint {
  val buildDirectory: DirectoryProperty = project.layout.buildDirectory
  models.map { it.output }.forEach { generatedProject ->
    filter {
      exclude {
        it.file.path.contains(buildDirectory.dir("generated/$generatedProject/src/main/").get().toString())
      }
    }
  }
}
