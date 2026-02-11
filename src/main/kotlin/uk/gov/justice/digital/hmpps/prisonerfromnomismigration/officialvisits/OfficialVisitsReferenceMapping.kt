package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits

import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.AttendanceType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.RelationshipType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.SearchLevelType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.VisitCompletionType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.VisitStatusType

fun CodeDescription.toDpsAttendanceType(): AttendanceType = when (code) {
  "ATT" -> AttendanceType.ATTENDED
  "ABS" -> AttendanceType.ABSENT
  else -> throw IllegalArgumentException("Unknown attendance type code: $code")
}

fun CodeDescription.toDpsRelationshipType(): RelationshipType = when (code) {
  "O" -> RelationshipType.OFFICIAL
  "S" -> RelationshipType.SOCIAL
  else -> throw IllegalArgumentException("Unknown relationship type code: $code")
}

fun CodeDescription.toDpsSearchLevelType(): SearchLevelType = when (code) {
  "FULL" -> SearchLevelType.FULL
  "PAT" -> SearchLevelType.PAT
  "RUB" -> SearchLevelType.RUB
  "RUB_A" -> SearchLevelType.RUB_A
  "RUB_B" -> SearchLevelType.RUB_B
  "STR" -> SearchLevelType.STR
  else -> throw IllegalArgumentException("Unknown search type code: $code")
}

fun CodeDescription.toDpsVisitStatusType(): VisitStatusType = when (code) {
  "CANC" -> VisitStatusType.CANCELLED
  "VDE" -> VisitStatusType.COMPLETED
  "HMPOP" -> VisitStatusType.COMPLETED
  "OFFEND" -> VisitStatusType.COMPLETED
  "VISITOR" -> VisitStatusType.COMPLETED
  "NORM" -> VisitStatusType.COMPLETED
  "SCH" -> VisitStatusType.SCHEDULED
  "EXP" -> VisitStatusType.EXPIRED
  else -> throw IllegalArgumentException("Unknown visit status code: $code")
}

fun CodeDescription?.toDpsVisitCompletionType(visitStatus: CodeDescription): VisitCompletionType? = when (this?.code) {
  "NO_VO" -> VisitCompletionType.STAFF_CANCELLED

  "VO_CANCEL" -> VisitCompletionType.STAFF_CANCELLED

  "REFUSED" -> VisitCompletionType.PRISONER_REFUSED

  "OFFCANC" -> VisitCompletionType.PRISONER_CANCELLED

  "VISCANC" -> VisitCompletionType.VISITOR_CANCELLED

  "NSHOW" -> VisitCompletionType.VISITOR_NO_SHOW

  "ADMIN" -> VisitCompletionType.STAFF_CANCELLED

  "ADMIN_CANCEL" -> VisitCompletionType.STAFF_CANCELLED

  "HMP" -> VisitCompletionType.STAFF_CANCELLED

  "NO_ID" -> VisitCompletionType.VISITOR_DENIED

  "BATCH_CANC" -> VisitCompletionType.STAFF_CANCELLED

  null -> when (visitStatus.code) {
    "VDE" -> VisitCompletionType.VISITOR_DENIED
    "HMPOP" -> VisitCompletionType.STAFF_EARLY
    "OFFEND" -> VisitCompletionType.PRISONER_EARLY
    "VISITOR" -> VisitCompletionType.VISITOR_EARLY
    "NORM" -> VisitCompletionType.NORMAL
    "SCH" -> null
    "EXP" -> VisitCompletionType.NORMAL
    else -> null
  }

  else -> null
}
