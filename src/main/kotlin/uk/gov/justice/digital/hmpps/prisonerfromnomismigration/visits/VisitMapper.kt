package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visits

import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisVisit

private val nomisCancellationOutcomes = mutableMapOf(
  NomisCancellationOutcome.ADMIN to VsipOutcome.ADMINISTRATIVE_CANCELLATION,
  NomisCancellationOutcome.HMP to VsipOutcome.ESTABLISHMENT_CANCELLED,
  NomisCancellationOutcome.NO_ID to VsipOutcome.VISITOR_FAILED_SECURITY_CHECKS,
  NomisCancellationOutcome.NO_VO to VsipOutcome.NO_VISITING_ORDER,
  NomisCancellationOutcome.NSHOW to VsipOutcome.VISITOR_DID_NOT_ARRIVE,
  NomisCancellationOutcome.OFFCANC to VsipOutcome.PRISONER_CANCELLED,
  NomisCancellationOutcome.REFUSED to VsipOutcome.PRISONER_REFUSED_TO_ATTEND,
  NomisCancellationOutcome.VISCANC to VsipOutcome.VISITOR_CANCELLED,
  NomisCancellationOutcome.VO_CANCEL to VsipOutcome.VISIT_ORDER_CANCELLED,
  NomisCancellationOutcome.BATCH_CANC to VsipOutcome.BATCH_CANCELLATION,
  NomisCancellationOutcome.ADMIN_CANCEL to VsipOutcome.ADMINISTRATIVE_CANCELLATION,
  NomisCancellationOutcome.REFUSED to VsipOutcome.PRISONER_REFUSED_TO_ATTEND,
)

private val nomisStatusToOutcomeMap = mutableMapOf(
  NomisVisitStatus.HMPOP to VsipOutcome.TERMINATED_BY_STAFF,
  NomisVisitStatus.CANC to VsipOutcome.CANCELLATION,
  NomisVisitStatus.OFFEND to VsipOutcome.PRISONER_COMPLETED_EARLY,
  NomisVisitStatus.VDE to VsipOutcome.VISITOR_DECLINED_ENTRY,
  NomisVisitStatus.VISITOR to VsipOutcome.VISITOR_COMPLETED_EARLY,
  NomisVisitStatus.NORM to VsipOutcome.COMPLETED_NORMALLY,
)

fun getVsipOutcome(nomisVisit: NomisVisit): VsipOutcome? =
  nomisVisit.visitOutcome?.let { nomisCancellationOutcomes[NomisCancellationOutcome.valueOf(it.code)] }
    ?: nomisStatusToOutcomeMap[NomisVisitStatus.valueOf(nomisVisit.visitStatus.code)]

fun getVsipVisitStatus(nomisVisit: NomisVisit): VsipStatus =
  if (nomisVisit.visitStatus.code == NomisVisitStatus.CANC.name) VsipStatus.CANCELLED else VsipStatus.BOOKED

enum class NomisVisitStatus {
  CANC,
  EXP,
  HMPOP,
  NORM,
  OFFEND,
  SCH,
  VISITOR,
  COMP,
  VDE,
}

enum class NomisCancellationOutcome {
  ADMIN,
  HMP,
  NO_ID,
  NO_VO,
  NSHOW,
  OFFCANC,
  REFUSED,
  VISCANC,
  VO_CANCEL,
  BATCH_CANC,
  ADMIN_CANCEL,
}

enum class VsipOutcome {
  ADMINISTRATIVE_CANCELLATION,
  BATCH_CANCELLATION,
  CANCELLATION,
  COMPLETED_NORMALLY,
  ESTABLISHMENT_CANCELLED,
  NO_VISITING_ORDER,
  PRISONER_CANCELLED,
  PRISONER_COMPLETED_EARLY,
  PRISONER_REFUSED_TO_ATTEND,
  TERMINATED_BY_STAFF,
  VISITOR_CANCELLED,
  VISITOR_COMPLETED_EARLY,
  VISITOR_DECLINED_ENTRY,
  VISITOR_DID_NOT_ARRIVE,
  VISITOR_FAILED_SECURITY_CHECKS,
  VISIT_ORDER_CANCELLED,
}

enum class VsipStatus {
  BOOKED,
  CANCELLED,
}
