package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes

import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CaseNoteResponse

fun CaseNoteResponse.toDPSCreateCaseNote() = MigrateCaseNoteRequest(
  dummyAttribute = this.caseNoteText,
  // TODO
)

fun CaseNoteResponse.toDPSCreateSyncCaseNote() = SyncCaseNoteRequest(
  dummyAttribute = this.caseNoteText,
//  text = this.caseNoteText!!,
//  locationId = "",
//  type = "",
//  subType = "",
//  occurrenceDateTime = LocalDateTime.now(),
  // TODO
)

fun CaseNoteResponse.toDPSUpdateCaseNote(dpsId: String) = SyncCaseNoteRequest(
  caseNoteId = dpsId,
  dummyAttribute = this.caseNoteText,
  // TODO
//   description = this.comment,
//   activeFrom = this.date,
//   activeTo = this.expiryDate,
//   authorisedBy = this.authorisedBy,
//   appendComment = null,
//
//    prisonId = nomisCaseNoteResponse.prisonId,
//    code = nomisCaseNoteResponse.caseNoteCode,
//    caseNoteType = toCaseNoteType(nomisCaseNoteResponse.caseNoteType),
//    lastUpdatedBy = nomisCaseNoteResponse.modifyUsername ?: nomisCaseNoteResponse.createUsername,
//    localName = nomisCaseNoteResponse.userDescription,
//    comments = nomisCaseNoteResponse.comment,
//    orderWithinParentCaseNote = nomisCaseNoteResponse.listSequence,
//    residentialHousingType = toResidentialHousingType(nomisCaseNoteResponse.unitType),
//
//    createDate = nomisCaseNoteResponse.createDatetime,
//    // lastModifiedDate - no value available as it changes with occupancy
//    isDeactivated = !nomisCaseNoteResponse.active,

)

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
