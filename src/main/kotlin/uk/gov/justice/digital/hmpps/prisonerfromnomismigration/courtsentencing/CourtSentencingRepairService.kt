package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Service
class CourtSentencingRepairService(
  val courtSentencingMigrationService: CourtSentencingMigrationService,
  val courtSentencingSynchronisationService: CourtSentencingSynchronisationService,
  val telemetryClient: TelemetryClient,
) {
  suspend fun resynchronisePrisonerCourtCases(offenderNo: String) {
    courtSentencingMigrationService.synchronisePrisonerCases(
      offenderNo = offenderNo,
      deleteExisting = true,
      context = MigrationContext(
        type = MigrationType.COURT_SENTENCING,
        migrationId = LocalDateTime.now().withNano(0).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
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

  suspend fun resynchronisePrisonerCourtCaseStatus(offenderNo: String, bookingId: Long, caseId: Long) {
    courtSentencingSynchronisationService.nomisCourtCaseUpdated(
      CourtCaseEvent(
        caseId = caseId,
        offenderIdDisplay = offenderNo,
        bookingId = bookingId,
        auditModuleName = "NOMIS",
      ),
    )

    telemetryClient.trackEvent(
      "court-sentencing-prisoner-case-status-repaired",
      mapOf(
        "offenderNo" to offenderNo,
        "nomisBookingId" to bookingId.toString(),
        "nomisCourtCaseId" to caseId.toString(),
      ),
      null,
    )
  }

  suspend fun resynchronisePrisonerSentenceTermInsert(
    offenderNo: String,
    bookingId: Long,
    sentenceSeq: Int,
    termSeq: Int,
  ) {
    courtSentencingSynchronisationService.nomisSentenceTermInserted(
      OffenderSentenceTermEvent(
        sentenceSeq = sentenceSeq,
        offenderIdDisplay = offenderNo,
        bookingId = bookingId,
        termSequence = termSeq,
        auditModuleName = "NOMIS",
      ),
    )

    telemetryClient.trackEvent(
      "court-sentencing-prisoner-sentence-term-repaired",
      mapOf(
        "offenderNo" to offenderNo,
        "nomisBookingId" to bookingId.toString(),
        "nomisSentenceSequence" to sentenceSeq.toString(),
        "nomisTermSequence" to termSeq.toString(),
      ),
      null,
    )
  }
}
