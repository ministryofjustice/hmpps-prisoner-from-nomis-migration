package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes

import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes.model.MigrateAmendmentRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes.model.MigrateCaseNoteRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes.model.SyncCaseNoteAmendmentRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes.model.SyncCaseNoteRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CaseNoteResponse
import java.time.LocalDateTime
import java.util.UUID

fun CaseNoteResponse.toDPSCreateCaseNote(offenderNo: String) = MigrateCaseNoteRequest(
  legacyId = this.caseNoteId,
  personIdentifier = offenderNo,
  // just 5 very early records with no prison which are all 'out':
  locationId = this.prisonId ?: "OUT",
  type = this.caseNoteType.code,
  subType = this.caseNoteSubType.code,
  // never null:
  text = this.caseNoteText!!,
  // never null in prod data:
  occurrenceDateTime = LocalDateTime.parse(this.occurrenceDateTime!!),
  systemGenerated = this.noteSourceCode == CaseNoteResponse.NoteSourceCode.AUTO,
  authorUsername = this.authorUsername,
  authorUserId = this.authorStaffId.toString(),
  authorName = this.authorName,
  createdDateTime = LocalDateTime.parse(this.createdDatetime),
  createdByUsername = "TBC",
  source = MigrateCaseNoteRequest.Source.NOMIS,
  amendments = this.amendments.map { a ->
    MigrateAmendmentRequest(
      text = a.text,
      authorUsername = a.authorUsername,
      authorUserId = a.authorUserId.toString(),
      authorName = a.authorName ?: "Unknown",
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
  text = this.caseNoteText!!,
  // never null in prod data:
  occurrenceDateTime = LocalDateTime.parse(this.occurrenceDateTime!!),
  systemGenerated = this.noteSourceCode == CaseNoteResponse.NoteSourceCode.AUTO,
  authorUsername = this.authorUsername,
  authorUserId = this.authorStaffId.toString(),
  authorName = this.authorName,
  createdDateTime = LocalDateTime.parse(this.createdDatetime),
  createdByUsername = "TBC",
  source = SyncCaseNoteRequest.Source.NOMIS,
  amendments = this.amendments.map { a ->
    SyncCaseNoteAmendmentRequest(
      text = a.text,
      authorUsername = a.authorUsername,
      authorUserId = a.authorUserId.toString(),
      authorName = a.authorName ?: "Unknown",
      createdDateTime = LocalDateTime.parse(a.createdDateTime),
    )
  }.toSet(),
)
