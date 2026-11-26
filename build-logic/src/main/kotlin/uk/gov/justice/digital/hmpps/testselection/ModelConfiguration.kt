package uk.gov.justice.digital.hmpps.testselection

import java.io.File

data class ModelConfiguration(val name: String, val packageName: String, val testPackageName: String? = null, val url: String, val models: String = "", val hasProductionVersion: Boolean = true) {
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

val packagePrefix = "uk.gov.justice.digital.hmpps.prisonerfromnomismigration"
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
