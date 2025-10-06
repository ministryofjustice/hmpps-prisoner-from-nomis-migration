package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType
import java.time.LocalDateTime
import kotlin.String

@Service
class CourtSentencingRepairService(
  val courtSentencingMigrationService: CourtSentencingMigrationService,
  val telemetryClient: TelemetryClient,
) {
  suspend fun resynchronisePrisonerCourtCases(offenderNo: String) {
    courtSentencingMigrationService.synchronisePrisonerCases(
      offenderNo = offenderNo,
      deleteExisting = true,
      context = MigrationContext(
        type = MigrationType.COURT_SENTENCING,
        migrationId = LocalDateTime.now().toString(),
        estimatedCount = 1,
        body = PrisonerId(offenderNo),
        properties = mutableMapOf(),

      ),
    ) {
      telemetryClient.trackEvent(
        "court-sentencing-prisoner-cases-repaired",
        mapOf(
          "offenderNo" to offenderNo,
        ),
        null,
      )
    }
  }
}
