import com.fasterxml.jackson.databind.ObjectMapper
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import org.jlleitschuh.gradle.ktlint.tasks.KtLintCheckTask
import org.jlleitschuh.gradle.ktlint.tasks.KtLintFormatTask
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask
import java.net.URI
import java.nio.file.Files
import java.nio.file.Paths

plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "8.3.7"
  kotlin("plugin.spring") version "2.2.10"
  id("org.openapi.generator") version "7.14.0"
}

configurations {
  implementation { exclude(module = "spring-boot-starter-web") }
  implementation { exclude(module = "spring-boot-starter-tomcat") }
  testImplementation { exclude(group = "org.junit.vintage") }
}

dependencies {
  implementation("uk.gov.justice.service.hmpps:hmpps-kotlin-spring-boot-starter:1.5.0")
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("uk.gov.justice.service.hmpps:hmpps-sqs-spring-boot-starter:5.4.10")
  implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")

  implementation("org.springdoc:springdoc-openapi-starter-webflux-ui:2.8.11")

  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk9")

  runtimeOnly("org.postgresql:r2dbc-postgresql:1.0.7.RELEASE")
  runtimeOnly("org.springframework.boot:spring-boot-starter-jdbc")
  runtimeOnly("org.postgresql:postgresql:42.7.7")
  runtimeOnly("org.flywaydb:flyway-database-postgresql")
  // Leaving at 1.43.0 to match the version used in App Insights https://github.com/microsoft/ApplicationInsights-Java/blob/3.6.2/dependencyManagement/build.gradle.kts#L14
  implementation("io.opentelemetry:opentelemetry-extension-kotlin:1.52.0")
  // Leaving at 2.9.0 to match the version used in App Insights https://github.com/microsoft/ApplicationInsights-Java/blob/3.6.2/dependencyManagement/build.gradle.kts#L16
  implementation("io.opentelemetry.instrumentation:opentelemetry-instrumentation-annotations:2.18.1")

  testImplementation("uk.gov.justice.service.hmpps:hmpps-kotlin-spring-boot-starter-test:1.5.0")
  testImplementation("io.swagger.parser.v3:swagger-parser:2.1.32") {
    exclude(group = "io.swagger.core.v3")
  }
  testImplementation("io.swagger.core.v3:swagger-core-jakarta:2.2.36")

  testImplementation("org.wiremock:wiremock-standalone:3.13.1")
  testImplementation("org.testcontainers:localstack:1.21.3")
  testImplementation("com.amazonaws:aws-java-sdk-core:1.12.788")
  testImplementation("org.awaitility:awaitility-kotlin:4.3.0")
  testImplementation("org.testcontainers:postgresql:1.21.3")
  testImplementation("io.mockk:mockk:1.14.5")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
}

kotlin {
  jvmToolchain(21)
  compilerOptions {
    freeCompilerArgs.addAll("-Xjvm-default=all", "-Xwhen-guards", "-Xannotation-default-target=param-property")
  }
}

data class ModelConfiguration(val name: String, val packageName: String, val testPackageName: String? = null, val url: String, val models: String = "") {
  fun toBuildModelTaskName(): String = "build${nameToCamel()}ApiModel"
  fun toWriteJsonTaskName(): String = "write${nameToCamel()}Json"
  fun toReadProductionVersionTaskName(): String = "read${nameToCamel()}ProductionVersion"
  fun toTestTaskName(): String = "test${nameToCamel()}"
  private val snakeRegex = "-[a-zA-Z]".toRegex()
  private fun nameToCamel(): String = snakeRegex.replace(name) {
    it.value.replace("-", "").uppercase()
  }.replaceFirstChar { it.uppercase() }
  val input: String
    get() = "openapi-specs/$name-api-docs.json"
  val output: String
    get() = name
}

val models = listOf(
  ModelConfiguration(
    name = "activities",
    packageName = "activities",
    testPackageName = "activities",
    url = "https://activities-api-dev.prison.service.justice.gov.uk/v3/api-docs",
    models = "ActivityMigrateRequest,ActivityMigrateResponse,AllocationMigrateRequest,AllocationMigrateResponse,AppointmentInstance,AppointmentMigrateRequest,NomisPayRate,NomisScheduleRule,Slot",
  ),
  ModelConfiguration(
    name = "alerts",
    packageName = "alerts",
    testPackageName = "alerts",
    url = "https://alerts-api-dev.hmpps.service.justice.gov.uk/v3/api-docs",
    models = "Alert,AlertCodeSummary,CreateAlert,ResyncAlert,ResyncedAlert,UpdateAlert",
  ),
  ModelConfiguration(
    name = "casenotes",
    packageName = "casenotes",
    testPackageName = "casenotes",
    url = "https://dev.offender-case-notes.service.justice.gov.uk/v3/api-docs",
    models = "Author,ErrorResponse,MoveCaseNotesRequest,SyncCaseNoteAmendmentRequest,SyncCaseNoteRequest,SyncResult",
  ),
  ModelConfiguration(
    name = "core-person",
    packageName = "coreperson",
    testPackageName = "coreperson",
    url = "https://hmpps-person-record-dev.hmpps.service.justice.gov.uk/v3/api-docs",
    models = "Address,AddressId,CreateResponse,Email,EmailId,Identifier,Names,PhoneId,PhoneNumber,Prisoner,Religion",
  ),
  ModelConfiguration(
    name = "court-sentencing",
    packageName = "courtsentencing",
    testPackageName = "courtsentencing",
    url = "https://remand-and-sentencing-api-dev.hmpps.service.justice.gov.uk/v3/api-docs",
  ),
  ModelConfiguration(
    name = "incidents",
    packageName = "incidents",
    testPackageName = "incidents",
    url = "https://incident-reporting-api-dev.hmpps.service.justice.gov.uk/v3/api-docs",
    models = "CorrectionRequest,DescriptionAddendum,HistoricalQuestion,HistoricalResponse,History,NomisCode,NomisHistory,NomisHistoryQuestion,NomisHistoryResponse,NomisOffender,NomisOffenderParty,NomisQuestion,NomisReport,NomisRequirement,NomisResponse,NomisStaff,NomisStaffParty,NomisStatus,NomisSyncReportId,NomisSyncRequest,PairStringListDescriptionAddendum,PrisonerInvolvement,Question,ReportBasic,ReportWithDetails,Response,SimplePageReportBasic,StaffInvolvement,StatusHistory",
  ),
  ModelConfiguration(
    name = "locations",
    packageName = "locations",
    testPackageName = "locations",
    url = "https://locations-inside-prison-api-dev.hmpps.service.justice.gov.uk/v3/api-docs",
    models = "Capacity,Certification,ChangeHistory,ErrorResponse,LegacyLocation,NomisSyncLocationRequest,NonResidentialUsageDto",
  ),
  ModelConfiguration(
    name = "nomis-prisoner",
    packageName = "nomisprisoner",
    url = "https://nomis-prisoner-api-dev.prison.service.justice.gov.uk/v3/api-docs",
  ),
  ModelConfiguration(
    name = "nomis-mapping-service",
    packageName = "nomismappings",
    url = "https://nomis-sync-prisoner-mapping-dev.hmpps.service.justice.gov.uk/v3/api-docs",
  ),
  ModelConfiguration(
    name = "organisations",
    packageName = "organisations",
    testPackageName = "organisations",
    url = "https://organisations-api-dev.hmpps.service.justice.gov.uk/v3/api-docs",
  ),
  ModelConfiguration(
    name = "personal-relationships",
    packageName = "personalrelationships",
    testPackageName = "personalrelationships",
    url = "https://personal-relationships-api-dev.hmpps.service.justice.gov.uk/v3/api-docs",
  ),
  ModelConfiguration(
    name = "sentencing-adjustments",
    packageName = "sentencing.adjustments",
    testPackageName = "sentencing",
    url = "https://adjustments-api-dev.hmpps.service.justice.gov.uk/v3/api-docs",
    models = "LegacyAdjustment,LegacyAdjustmentCreatedResponse",
  ),
  ModelConfiguration(
    name = "visit-balance",
    packageName = "visit.balance",
    testPackageName = "visitbalances",
    url = "https://hmpps-visit-allocation-api-dev.prison.service.justice.gov.uk/v3/api-docs",
    models = "VisitAllocationPrisonerMigrationDto,VisitAllocationPrisonerSyncDto",
  ),
  ModelConfiguration(
    name = "finance",
    packageName = "finance",
    testPackageName = "finance",
    url = "https://prisoner-finance-poc-api-dev.hmpps.service.justice.gov.uk/v3/api-docs",
  ),
)

tasks {
  withType<KotlinCompile> {
    dependsOn(models.map { it.toBuildModelTaskName() })
    compilerOptions.jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
  }
  withType<KtLintCheckTask> {
    // Under gradle 8 we must declare the dependency here, even if we're not going to be linting the model
    mustRunAfter(models.map { it.toBuildModelTaskName() })
  }
  withType<KtLintFormatTask> {
    // Under gradle 8 we must declare the dependency here, even if we're not going to be linting the model
    mustRunAfter(models.map { it.toBuildModelTaskName() })
  }
}
val separateTestPackages = mutableListOf<String>()
models.forEach {
  tasks.register(it.toBuildModelTaskName(), GenerateTask::class) {
    group = "Generate model from API JSON definition"
    description = "Generate model from API JSON definition for ${it.name}"
    generatorName.set("kotlin")
    skipValidateSpec.set(true)
    inputSpec.set(it.input)
    outputDir.set("$buildDirectory/generated/${it.output}")
    modelPackage.set("uk.gov.justice.digital.hmpps.prisonerfromnomismigration.${it.packageName}.model")
    apiPackage.set("uk.gov.justice.digital.hmpps.prisonerfromnomismigration.${it.packageName}.api")
    configOptions.set(configValues)
    globalProperties.set(mapOf("models" to it.models))
    generateModelTests.set(false)
    generateModelDocumentation.set(false)
  }
  tasks.register(it.toWriteJsonTaskName()) {
    group = "Write JSON"
    description = "Write JSON for ${it.name}"
    doLast {
      val json = URI.create(it.url).toURL().readText()
      val formattedJson = ObjectMapper().let { mapper ->
        mapper.writerWithDefaultPrettyPrinter().writeValueAsString(mapper.readTree(json))
      }
      Files.write(Paths.get(it.input), formattedJson.toByteArray())
    }
  }
  tasks.register(it.toReadProductionVersionTaskName()) {
    group = "Read current production version"
    description = "Read current production version for ${it.name}"
    doLast {
      val productionUrl = it.url.replace("-dev".toRegex(), "")
        .replace("dev.".toRegex(), "")
        .replace("/v3/api-docs".toRegex(), "/info")
      val json = URI.create(productionUrl).toURL().readText()
      val version = ObjectMapper().readTree(json).at("/build/version").asText()
      println(version)
    }
  }
  if (it.testPackageName != null) {
    val test by testing.suites.existing(JvmTestSuite::class)
    separateTestPackages.add(it.testPackageName)
    val task = tasks.register<Test>(it.toTestTaskName()) {
      testClassesDirs = files(test.map { it.sources.output.classesDirs })
      classpath = files(test.map { it.sources.runtimeClasspath })
      group = "Run tests"
      description = "Run tests for ${it.name}"
      shouldRunAfter("test")
      useJUnitPlatform()
      filter {
        includeTestsMatching("uk.gov.justice.digital.hmpps.prisonerfromnomismigration.${it.testPackageName}.*")
      }
    }
    tasks.check { dependsOn(task) }
  }
}

tasks.test {
  filter {
    separateTestPackages.forEach {
      excludeTestsMatching("uk.gov.justice.digital.hmpps.prisonerfromnomismigration.$it.*")
    }
  }
}

val buildDirectory: Directory = layout.buildDirectory.get()
val configValues = mapOf(
  "dateLibrary" to "java8-localdatetime",
  "serializationLibrary" to "jackson",
  "enumPropertyNaming" to "original",
)

kotlin {
  models.map { it.output }.forEach { generatedProject ->
    sourceSets["main"].apply {
      kotlin.srcDir("$buildDirectory/generated/$generatedProject/src/main/kotlin")
    }
  }
}

configure<KtlintExtension> {
  models.map { it.output }.forEach { generatedProject ->
    filter {
      exclude {
        it.file.path.contains("$buildDirectory/generated/$generatedProject/src/main/")
      }
    }
  }
}
