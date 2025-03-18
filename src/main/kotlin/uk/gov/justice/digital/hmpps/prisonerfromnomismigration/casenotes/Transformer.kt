package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes

import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes.model.Author
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes.model.SyncCaseNoteAmendmentRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes.model.SyncCaseNoteRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CaseNoteResponse
import java.util.UUID

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
  occurrenceDateTime = this.occurrenceDateTime!!,
  systemGenerated = this.noteSourceCode == CaseNoteResponse.NoteSourceCode.AUTO,
  author = Author(
    username = this.authorUsername,
    userId = this.authorStaffId.toString(),
    firstName = this.authorFirstName ?: "",
    lastName = this.authorLastName,
  ),
  createdDateTime = this.creationDateTime!!,
  createdByUsername = this.createdUsername,
  system = SyncCaseNoteRequest.System.valueOf(this.sourceSystem.name),
  amendments = this.amendments.map { a ->
    SyncCaseNoteAmendmentRequest(
      text = a.text,
      author = Author(
        username = a.authorUsername,
        userId = a.authorStaffId.toString(),
        firstName = a.authorFirstName ?: "",
        lastName = a.authorLastName ?: "",
      ),
      createdDateTime = a.createdDateTime,
      system = SyncCaseNoteAmendmentRequest.System.valueOf(a.sourceSystem.name),
    )
  }.toSet(),
)
