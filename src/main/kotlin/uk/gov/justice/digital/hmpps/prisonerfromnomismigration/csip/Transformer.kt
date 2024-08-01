package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip

import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.CreateContributoryFactorRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.CreateCsipRecordRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.CreateReferralRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.CreateSaferCustodyScreeningOutcomeRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.UpdateContributoryFactorRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.UpdateCsipRecordRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.UpdateDecisionAndActionsRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.UpdateInvestigationRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.UpdatePlanRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.UpdateReferral
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.UpdateReferral.IsSaferCustodyTeamInformed.NO
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.UpdateReferral.IsSaferCustodyTeamInformed.YES
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.Actions
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CSIPFactorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CSIPResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.Decision
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.InvestigationDetails
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.SaferCustodyScreening

// TODO This needs updating once csip api updated
fun CSIPResponse.toDPSMigrateCSIP() =
  CreateCsipRecordRequest(
    logCode = logNumber,
    referral =
    CreateReferralRequest(
      incidentDate = incidentDate,
      incidentTime = incidentTime,
      incidentTypeCode = type.code,
      incidentLocationCode = location.code,
      referredBy = reportedBy,
      refererAreaCode = areaOfWork.code,
      isProactiveReferral = proActiveReferral,
      isStaffAssaulted = staffAssaulted,
      assaultedStaffName = staffAssaultedName,
      incidentInvolvementCode = reportDetails.involvement?.code,
      descriptionOfConcern = reportDetails.concern,
      knownReasons = reportDetails.knownReasons,
      contributoryFactors = listOf(),
      otherInformation = reportDetails.otherInformation,
      isSaferCustodyTeamInformed = if (reportDetails.saferCustodyTeamInformed) {
        CreateReferralRequest.IsSaferCustodyTeamInformed.YES
      } else {
        CreateReferralRequest.IsSaferCustodyTeamInformed.NO
      },
      isReferralComplete = reportDetails.referralComplete,

    ),
  )

// ////// OIDCSIPN ////////////////////////////
fun CSIPResponse.toDPSCreateRequest() =
  CreateCsipRecordRequest(
    logCode = logNumber,
    referral = CreateReferralRequest(
      incidentDate = incidentDate,
      incidentTime = incidentTime,
      incidentTypeCode = type.code,
      incidentLocationCode = location.code,
      referredBy = reportedBy,
      refererAreaCode = areaOfWork.code,
      isProactiveReferral = proActiveReferral,
      isStaffAssaulted = staffAssaulted,
      assaultedStaffName = staffAssaultedName,
      contributoryFactors = listOf(),
      isSaferCustodyTeamInformed = CreateReferralRequest.IsSaferCustodyTeamInformed.DO_NOT_KNOW,
    ),
  )

// From OIDCSIPN - everything that's the same as the create request but also need lognumber update
fun CSIPResponse.toDPSUpdateCsipRecordRequest() =
  UpdateCsipRecordRequest(
    logCode = logNumber,
    UpdateReferral(
      incidentDate = incidentDate,
      incidentTypeCode = type.code,
      incidentLocationCode = location.code,
      referredBy = reportedBy,
      refererAreaCode = areaOfWork.code,
      incidentTime = incidentTime,
      isProactiveReferral = proActiveReferral,
      isStaffAssaulted = staffAssaulted,
      assaultedStaffName = staffAssaultedName,
      isSaferCustodyTeamInformed = if (reportDetails.saferCustodyTeamInformed) {
        YES
      } else {
        NO
      },
    ),
  )

// ////// OIDCSIPC ////////////////////////////
fun CSIPResponse.toDPSUpdateReferralContRequest() =
  UpdateCsipRecordRequest(
    // needed but not changed here
    referral = UpdateReferral(
      incidentDate = incidentDate,
      // needed but not changed here
      incidentTypeCode = type.code,
      // needed but not changed here
      incidentLocationCode = location.code,
      // needed but not changed here
      referredBy = reportedBy,
      // needed but not changed here
      refererAreaCode = areaOfWork.code,

      isSaferCustodyTeamInformed = if (reportDetails.saferCustodyTeamInformed) {
        YES
      } else {
        NO
      },
      incidentInvolvementCode = reportDetails.involvement?.code,
      descriptionOfConcern = reportDetails.concern,
      knownReasons = reportDetails.knownReasons,
      otherInformation = reportDetails.otherInformation,
      isReferralComplete = reportDetails.referralComplete,
    ),
  )

// ////// From OIDCSIPS - Safer Custody Screening ////////////////////////////
// We do not need an update SCS as once entered, it can not be altered
fun SaferCustodyScreening.toDPSCreateCSIPSCS() =
  CreateSaferCustodyScreeningOutcomeRequest(
    outcomeTypeCode = outcome!!.code,
    date = this.recordedDate!!,
    reasonForDecision = reasonForDecision!!,
    recordedBy = recordedBy!!,
    recordedByDisplayName = recordedByDisplayName!!,
  )

// ////// From OIDCSIPC - but FACTORS-CREATED/UPDATED event ////////////////////////////
fun CSIPFactorResponse.toDPSCreateFactorRequest() =
  CreateContributoryFactorRequest(
    factorTypeCode = type.code,
    comment = comment,
  )
fun CSIPFactorResponse.toDPSUpdateFactorRequest() =
  UpdateContributoryFactorRequest(
    factorTypeCode = type.code,
    comment = comment,
  )

// ////// OIDCSIPI - Investigation ////////////////////////////
fun InvestigationDetails.toDPSUpdateInvestigationRequest() =
  UpdateInvestigationRequest(
    staffInvolved = staffInvolved,
    evidenceSecured = evidenceSecured,
    occurrenceReason = reasonOccurred,
    personsUsualBehaviour = usualBehaviour,
    personsTrigger = trigger,
    protectiveFactors = protectiveFactors,
  )

/*
fun InterviewDetails.toDPSCreateInterviewRequest() =
  CreateInterviewRequest(
    interviewee = interviewee,
    interviewDate = date,
    intervieweeRoleCode = role.code,
    interviewText = comments,
  )
fun InterviewDetails.toDPSUpdateInterviewRequest() =
  UpdateInterviewRequest(
    interviewee = interviewee,
    interviewDate = date,
    intervieweeRoleCode = role.code,
    interviewText = comments,
  )

*/
// ////// OIDCSIPD - Decisions & Actions ////////////////////////////
fun Decision.toDPSUpdateDecisionsAndActionsRequest() =
  UpdateDecisionAndActionsRequest(
    outcomeTypeCode = decisionOutcome!!.code,
    conclusion = conclusion,
    signedOffByRoleCode = signedOffRole?.code,
    recordedBy = recordedBy,
    recordedByDisplayName = recordedByDisplayName,
    date = recordedDate,
    nextSteps = nextSteps,
    actions = actions.toDPSActions(),
    actionOther = otherDetails,
  )

fun Actions.toDPSActions(): MutableSet<UpdateDecisionAndActionsRequest.Actions> {
  val dpsActions: MutableSet<UpdateDecisionAndActionsRequest.Actions> = mutableSetOf()
  dpsActions.addIfTrue(openCSIPAlert, UpdateDecisionAndActionsRequest.Actions.OpenCsipAlert)
  dpsActions.addIfTrue(nonAssociationsUpdated, UpdateDecisionAndActionsRequest.Actions.NonAssociationsUpdated)
  dpsActions.addIfTrue(observationBook, UpdateDecisionAndActionsRequest.Actions.ObservationBook)
  dpsActions.addIfTrue(unitOrCellMove, UpdateDecisionAndActionsRequest.Actions.UnitOrCellMove)
  dpsActions.addIfTrue(csraOrRsraReview, UpdateDecisionAndActionsRequest.Actions.CsraOrRsraReview)
  dpsActions.addIfTrue(serviceReferral, UpdateDecisionAndActionsRequest.Actions.ServiceReferral)
  dpsActions.addIfTrue(simReferral, UpdateDecisionAndActionsRequest.Actions.SimReferral)
  return dpsActions
}

fun MutableSet<UpdateDecisionAndActionsRequest.Actions>.addIfTrue(actionSet: Boolean, action: UpdateDecisionAndActionsRequest.Actions) {
  if (actionSet) this.add(action)
}

// ////// OIDCSIPP - Plan ////////////////////////////
fun CSIPResponse.toDPSUpdatePlanRequest() =
  UpdatePlanRequest(
    caseManager = caseManager!!,
    reasonForPlan = planReason!!,
    firstCaseReviewDate = firstCaseReviewDate!!,
  )
/*
fun Plan.toDPSCreateIdentifiedNeedsRequest() =
  CreateIdentifiedNeedRequest(
    identifiedNeed = identifiedNeed,
    needIdentifiedBy = referredBy!!,
    createdDate = createdDate,
    targetDate = targetDate,
    intervention = intervention,
    closedDate = closedDate,
    progression = progression,
  )
fun Plan.toDPSUpdateIdentifiedNeedsRequest() =
  UpdateIdentifiedNeedRequest(
    identifiedNeed = identifiedNeed,
    needIdentifiedBy = referredBy!!,
    createdDate = createdDate,
    targetDate = targetDate,
    intervention = intervention,
    closedDate = closedDate,
    progression = progression,
  )

// ////// OIDCSIPR - Review ////////////////////////////
fun Review.toDPSCreateReviewRequest() =
  CreateReviewRequest(
    recordedBy = recordedBy,
    recordedByDisplayName = recordedByDisplayName!!,
    reviewDate = recordedDate,
    nextReviewDate = nextReviewDate,
    isActionResponsiblePeopleInformed = peopleInformed,
    isActionCsipUpdated = csipUpdated,
    isActionRemainOnCsip = remainOnCSIP,
    isActionCaseNote = caseNote,
    isActionCloseCsip = closeCSIP,
    csipClosedDate = closeDate,
    // Note - no attendee information
    summary = summary,
  )

fun Review.toDPSUpdateReviewRequest() =
  UpdateReviewRequest(
    recordedBy = recordedBy,
    recordedByDisplayName = recordedByDisplayName!!,
    reviewDate = recordedDate,
    nextReviewDate = nextReviewDate,
    isActionResponsiblePeopleInformed = peopleInformed,
    isActionCsipUpdated = csipUpdated,
    isActionRemainOnCsip = remainOnCSIP,
    isActionCaseNote = caseNote,
    isActionCloseCsip = closeCSIP,
    csipClosedDate = closeDate,
    summary = summary,
  )

fun Attendee.toCreateAttendeeRequest() =
  CreateAttendeeRequest(
    name = name,
    role = this.role,
    isAttended = attended,
    contribution = contribution,
  )
*/
