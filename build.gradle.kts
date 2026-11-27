import uk.gov.justice.digital.hmpps.testselection.ModelConfiguration

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

val models = listOf(
  ModelConfiguration(
    name = "bedassignment",
    packageName = "bedassignment",
    testPackageName = "bedassignment",
    url = "https://change-someones-cell-api-dev.hmpps.service.justice.gov.uk/v3/api-docs",
  ),
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
  ),
  ModelConfiguration(
    name = "csra",
    packageName = "csra",
    testPackageName = "csra",
    url = "https://cell-sharing-risk-assessment-api-dev.hmpps.service.justice.gov.uk/v3/api-docs",
  ),
  ModelConfiguration(
    name = "court-scheduler",
    packageName = "courtscheduler",
    url = "https://court-appearance-scheduler-api-dev.hmpps.service.justice.gov.uk/v3/api-docs",
  ),
  ModelConfiguration(
    name = "court-sentencing",
    packageName = "courtsentencing",
    testPackageName = "courtsentencing",
    url = "https://remand-and-sentencing-api-dev.hmpps.service.justice.gov.uk/v3/api-docs",
  ),
  ModelConfiguration(
    name = "drug-testing",
    packageName = "drugtesting",
    url = "https://mandatory-drug-testing-api-dev.hmpps.service.justice.gov.uk/v3/api-docs",
    hasProductionVersion = false,
  ),
  ModelConfiguration(
    name = "finance",
    packageName = "finance",
    testPackageName = "finance",
    url = "https://prisoner-finance-sync-api-dev.hmpps.service.justice.gov.uk/v3/api-docs",
  ),
  ModelConfiguration(
    name = "locations",
    packageName = "locations",
    testPackageName = "locations",
    url = "https://locations-inside-prison-api-dev.hmpps.service.justice.gov.uk/v3/api-docs",
    models = "Capacity,Certification,ChangeHistory,ErrorResponse,LegacyLocation,NomisSyncLocationRequest,NonResidentialUsageDto",
  ),
  ModelConfiguration(
    name = "movements",
    packageName = "movements",
    testPackageName = "movements",
    url = "https://external-movements-api-dev.hmpps.service.justice.gov.uk/v3/api-docs",
    models = "Address,NomisAudit,ScheduledTemporaryAbsenceRequest,SyncResponse,TapApplicationRequest,TapLocation,TapMovementRequest",
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
    name = "nomis-update",
    packageName = "nomisupdate",
    url = "https://prisoner-to-nomis-update-dev.hmpps.service.justice.gov.uk/v3/api-docs",
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
    name = "property",
    packageName = "property",
    url = "https://prisoner-property-api-dev.hmpps.service.justice.gov.uk/v3/api-docs",
  ),
  ModelConfiguration(
    name = "sentencing-adjustments",
    packageName = "sentencing.adjustments",
    testPackageName = "sentencing",
    url = "https://adjustments-api-dev.hmpps.service.justice.gov.uk/v3/api-docs",
  ),
  ModelConfiguration(
    name = "staff",
    packageName = "staff",
    testPackageName = "staff",
    url = "https://prison-users-api-dev.prison.service.justice.gov.uk/v3/api-docs",
    hasProductionVersion = false,
  ),
  ModelConfiguration(
    name = "visit-balance",
    packageName = "visit.balance",
    testPackageName = "visitbalances",
    url = "https://hmpps-visit-allocation-api-dev.prison.service.justice.gov.uk/v3/api-docs",
  ),
  ModelConfiguration(
    name = "officialvisits",
    packageName = "officialvisits",
    testPackageName = "officialvisits",
    url = "https://official-visits-api-dev.hmpps.service.justice.gov.uk/v3/api-docs",
  ),
  ModelConfiguration(
    name = "transfer-schedule",
    packageName = "transferschedule",
    url = "https://transfer-scheduler-api-dev.hmpps.service.justice.gov.uk/v3/api-docs",
  ),
  ModelConfiguration(
    name = "agency-registers",
    packageName = "agencyregisters",
    testPackageName = "agency",
    url = "https://prison-register-dev.hmpps.service.justice.gov.uk/v3/api-docs",
  ),
  ModelConfiguration(
    name = "calculate-release-dates",
    packageName = "calculatereleasedates",
    testPackageName = "calculatereleasedates",
    url = "https://calculate-release-dates-api-dev.hmpps.service.justice.gov.uk/v3/api-docs",
  ),
  ModelConfiguration(
    name = "person-location",
    packageName = "personlocation",
    url = "https://person-location-api-dev.hmpps.service.justice.gov.uk/v3/api-docs",
  ),
)

selectiveTests {
  modelConfigurations.set(models)
  packagePrefix.set("uk.gov.justice.digital.hmpps.prisonerfromnomismigration")
}

tasks.test {
  dependsOn(gradle.includedBuild("build-logic").task(":test"))
  maxHeapSize = "1024m"
}

tasks.ktlintCheck {
  dependsOn(gradle.includedBuild("build-logic").task(":ktlintCheck"))
}

tasks.ktlintFormat {
  dependsOn(gradle.includedBuild("build-logic").task(":ktlintFormat"))
}
