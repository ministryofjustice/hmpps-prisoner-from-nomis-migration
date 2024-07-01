package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes

import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CaseNoteResponse

fun CaseNoteResponse.toDPSCreateCaseNote() = MigrateCaseNoteRequest(
  dummyAttribute = this.caseNoteText,
  // TODO
)

// fun CaseNoteResponse.toDPSUpdateCaseNote() = UpdateCaseNote(
//   description = this.comment,
//   activeFrom = this.date,
//   activeTo = this.expiryDate,
//   authorisedBy = this.authorisedBy,
//   appendComment = null,
// )

fun CaseNoteResponse.toDPSMigratedCaseNote() = DpsCaseNote(
  dummyAttribute = this.caseNoteText,
  caseNoteId = this.caseNoteId.toString(),
  // TODO
)

// fun CaseNoteResponse.toDPSMergeCaseNote() = MergeCaseNote(
//   offenderBookId = this.bookingId,
//   alertSeq = this.alertSequence.toInt(),
//   alertCode = this.alertCode.code,
//   description = this.comment,
//   activeFrom = this.date,
//   activeTo = this.expiryDate,
//   authorisedBy = this.authorisedBy,
// )
