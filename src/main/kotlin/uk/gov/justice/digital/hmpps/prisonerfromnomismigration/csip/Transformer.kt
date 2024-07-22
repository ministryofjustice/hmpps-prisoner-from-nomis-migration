package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip

import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.CreateContributoryFactorRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.CreateCsipRecordRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.CreateReferralRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.CreateSaferCustodyScreeningOutcomeRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.UpdateContributoryFactorRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.UpdateInvestigationRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.UpdateReferralRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CSIPFactorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CSIPResponse
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
      isSaferCustodyTeamInformed = if (reportDetails.saferCustodyTeamInformed) CreateReferralRequest.IsSaferCustodyTeamInformed.yes else CreateReferralRequest.IsSaferCustodyTeamInformed.no,
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
      isSaferCustodyTeamInformed = CreateReferralRequest.IsSaferCustodyTeamInformed.doMinusNotMinusKnow,
    ),
  )

// From OIDCSIPN - everything that's the same as the create request but also need lognumber update
fun CSIPResponse.toDPSUpdateReferralRequest() =
  UpdateReferralRequest(
    // TODO Add in when csip api updated
    // logCode= logNumber,
    incidentDate = incidentDate,
    incidentTypeCode = type.code,
    incidentLocationCode = location.code,
    referredBy = reportedBy,
    refererAreaCode = areaOfWork.code,
    incidentTime = incidentTime,
    isProactiveReferral = proActiveReferral,
    isStaffAssaulted = staffAssaulted,
    assaultedStaffName = staffAssaultedName,
    isSaferCustodyTeamInformed = if (reportDetails.saferCustodyTeamInformed) UpdateReferralRequest.IsSaferCustodyTeamInformed.yes else UpdateReferralRequest.IsSaferCustodyTeamInformed.no,
  )

// ////// OIDCSIPC ////////////////////////////
// TODO we would never know when to do a create
fun CSIPResponse.toDPSUpdateReferralContRequest() =
  UpdateReferralRequest(
    // needed but not changed here
    incidentDate = incidentDate,
    // needed but not changed here
    incidentTypeCode = type.code,
    // needed but not changed here
    incidentLocationCode = location.code,
    // needed but not changed here
    referredBy = reportedBy,
    // needed but not changed here
    refererAreaCode = areaOfWork.code,
    incidentInvolvementCode = reportDetails.involvement?.code,
    descriptionOfConcern = reportDetails.concern,
    knownReasons = reportDetails.knownReasons,
    otherInformation = reportDetails.otherInformation,
    isSaferCustodyTeamInformed = if (reportDetails.saferCustodyTeamInformed) UpdateReferralRequest.IsSaferCustodyTeamInformed.yes else UpdateReferralRequest.IsSaferCustodyTeamInformed.no,
    isReferralComplete = reportDetails.referralComplete,
  )

// ////// From OIDCSIPS - Safer Custody Screening ////////////////////////////
// We do not need an update SCS as once entered, it can not be altered
fun SaferCustodyScreening.toDPSCreateCSIPSCS() =
  CreateSaferCustodyScreeningOutcomeRequest(
    outcomeTypeCode = outcome!!.code,
    date = this.recordedDate!!,
    reasonForDecision = reasonForDecision!!,
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
// TODO we would never know when to do a create Investigation as it is from the CSIP_REPORTS-UPDATED event
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


// ////// OIDCSIPD - Decisions & Actions ////////////////////////////
fun Decision.toDPSUpdateDecisionsAndActionsRequest() =
  UpdateDecisionAndActionsRequest(
    outcomeTypeCode = decisionOutcome!!.code,
    conclusion = conclusion,
    outcomeSignedOffByRoleCode = signedOffRole?.code,
    outcomeRecordedBy = recordedBy,
    outcomeRecordedByDisplayName = recordedByDisplayName,
    outcomeDate = recordedDate,
    nextSteps = nextSteps,
    isActionOpenCsipAlert = actions.openCSIPAlert,
    isActionNonAssociationsUpdated = actions.nonAssociationsUpdated,
    isActionObservationBook = actions.observationBook,
    isActionUnitOrCellMove = actions.unitOrCellMove,
    isActionCsraOrRsraReview = actions.csraOrRsraReview,
    isActionServiceReferral = actions.serviceReferral,
    isActionSimReferral = actions.simReferral,
    actionOther = otherDetails,
  )

// ////// OIDCSIPP - Plan ////////////////////////////
// TODO we would never know when to do a create Decisions as it is from the CSIP_REPORTS-UPDATED event
 SO CREATE Not needed as don't know if create or update
fun CSIPResponse.toDPSCreatePlanRequest() =
CreatePlanRequest(
caseManager = caseManager!!,
reasonForPlan = planReason!!,
firstCaseReviewDate = firstCaseReviewDate!!,
identifiedNeeds = listOf()
)

fun CSIPResponse.toDPSUpdatePlanRequest() =
  UpdatePlanRequest(
    caseManager = caseManager!!,
    reasonForPlan = planReason!!,
    firstCaseReviewDate = firstCaseReviewDate!!,
  )

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
    recordedBy = createdBy,
    recordedByDisplayName = createdByDisplayName!!,
    reviewDate = LocalDateTime.parse(createDateTime).toLocalDate(),
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
    recordedBy = createdBy,
    recordedByDisplayName = createdByDisplayName!!,
    reviewDate = LocalDateTime.parse(createDateTime).toLocalDate(),
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
