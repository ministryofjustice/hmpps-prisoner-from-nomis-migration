package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes

import org.slf4j.LoggerFactory
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes.model.Author
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes.model.MigrateAmendmentRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes.model.MigrateCaseNoteRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes.model.SyncCaseNoteAmendmentRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes.model.SyncCaseNoteRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CaseNoteResponse
import java.time.LocalDateTime
import java.util.UUID

fun CaseNoteResponse.toDPSCreateCaseNote(offenderNo: String) = MigrateCaseNoteRequest(
  legacyId = this.caseNoteId,
  // just 5 very early records with no prison which are all 'out':
  locationId = this.prisonId ?: "OUT",
  // 6 records with incorrect code, see https://mojdt.slack.com/archives/C06G85DCF8T/p1727086140883849?thread_ts=1726847680.924269&cid=C06G85DCF8T
  type = if (caseNoteType.code == "CNOTE" && caseNoteSubType.code == "OUTCOME") {
    "APP".also {
      LoggerFactory.getLogger(this::class.java).warn("CNOTE/OUTCOME detected and corrected for $offenderNo, $caseNoteId")
    }
  } else {
    caseNoteType.code
  },
  subType = this.caseNoteSubType.code,
  // never null:
  text = this.caseNoteText,
  // never null in prod data:
  occurrenceDateTime = LocalDateTime.parse(this.occurrenceDateTime!!),
  systemGenerated = this.noteSourceCode == CaseNoteResponse.NoteSourceCode.AUTO,
  author = Author(
    username = this.authorUsername,
    userId = this.authorStaffId.toString(),
    firstName = this.authorFirstName ?: "",
    lastName = this.authorLastName,
  ),
  createdDateTime = LocalDateTime.parse(this.createdDatetime),
  createdByUsername = this.createdUsername,
  source = MigrateCaseNoteRequest.Source.NOMIS,
  amendments = this.amendments.map { a ->
    MigrateAmendmentRequest(
      text = a.text,
      author = Author(
        username = a.authorUsername,
        userId = a.authorStaffId.toString(),
        firstName = a.authorFirstName ?: "",
        lastName = a.authorLastName ?: "",
      ),
      createdDateTime = LocalDateTime.parse(a.createdDateTime),
    )
  }.toSet(),
)

fun CaseNoteResponse.toDPSSyncCaseNote(offenderNo: String, id: UUID? = null) = SyncCaseNoteRequest(
  id = id,
  legacyId = this.caseNoteId,
  personIdentifier = offenderNo,
  // just 5 very early records with no prison which are all 'out':
  locationId = this.prisonId ?: "OUT",
  type = this.caseNoteType.code,
  subType = this.caseNoteSubType.code,
  // never null:
  text = this.caseNoteText,
  // never null in prod data:
  occurrenceDateTime = LocalDateTime.parse(this.occurrenceDateTime!!),
  systemGenerated = this.noteSourceCode == CaseNoteResponse.NoteSourceCode.AUTO,
  author = Author(
    username = this.authorUsername,
    userId = this.authorStaffId.toString(),
    firstName = this.authorFirstName ?: "",
    lastName = this.authorLastName,
  ),
  createdDateTime = LocalDateTime.parse(this.createdDatetime),
  createdByUsername = this.createdUsername,
  source = SyncCaseNoteRequest.Source.NOMIS,
  amendments = this.amendments.map { a ->
    SyncCaseNoteAmendmentRequest(
      text = a.text,
      author = Author(
        username = a.authorUsername,
        userId = a.authorStaffId.toString(),
        firstName = a.authorFirstName ?: "",
        lastName = a.authorLastName ?: "",
      ),
      createdDateTime = LocalDateTime.parse(a.createdDateTime),
    )
  }.toSet(),
)
