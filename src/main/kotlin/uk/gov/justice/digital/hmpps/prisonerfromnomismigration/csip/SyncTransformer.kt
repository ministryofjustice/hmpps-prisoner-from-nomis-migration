package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip

import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.SyncAttendeeRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.SyncContributoryFactorRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.SyncCsipRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.SyncDecisionAndActionsRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.SyncInterviewRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.SyncInvestigationRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.SyncNeedRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.SyncPlanRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.SyncReferralRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.SyncReviewRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.SyncScreeningOutcomeRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.Actions
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.Attendee
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CSIPFactorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CSIPResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.Decision
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.InterviewDetails
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.InvestigationDetails
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.Plan
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.Review
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.SaferCustodyScreening
import java.time.LocalDateTime
import java.util.UUID

fun CSIPResponse.toDPSSyncRequest(dpsReportId: String? = null, actioned: ActionDetails) =
  SyncCsipRequest(
    id = dpsReportId ?.let { UUID.fromString(dpsReportId) },
    legacyId = id,
    prisonNumber = originalAgencyId!!,
    logCode = logNumber,
    activeCaseloadId = originalAgencyId,

    referral =
    SyncReferralRequest(
      incidentDate = incidentDate,
      incidentTime = incidentTime,
      incidentTypeCode = type.code,
      incidentLocationCode = location.code,
      referredBy = reportedBy,
      referralDate = reportedDate,
      refererAreaCode = areaOfWork.code,
      isProactiveReferral = proActiveReferral,
      isStaffAssaulted = staffAssaulted,
      assaultedStaffName = staffAssaultedName,
      incidentInvolvementCode = reportDetails.involvement?.code,
      descriptionOfConcern = reportDetails.concern,
      knownReasons = reportDetails.knownReasons,
      otherInformation = reportDetails.otherInformation,
      isSaferCustodyTeamInformed = if (reportDetails.saferCustodyTeamInformed) {
        SyncReferralRequest.IsSaferCustodyTeamInformed.YES
      } else {
        SyncReferralRequest.IsSaferCustodyTeamInformed.NO
      },
      isReferralComplete = reportDetails.referralComplete,
      completedDate = reportDetails.referralCompletedDate,
      completedBy = reportDetails.referralCompletedBy,
      completedByDisplayName = reportDetails.referralCompletedByDisplayName,
      contributoryFactors = reportDetails.factors.map { it.toDPSSyncContributoryFactorRequest() },
      saferCustodyScreeningOutcome = saferCustodyScreening.outcome ?.let { saferCustodyScreening.toDPSSyncCSIPSCS() },
      investigation = investigation.toDPSSyncInvestigationRequest(),
      decisionAndActions = decision.decisionOutcome ?.let { decision.toDPSSyncDecisionsAndActionsRequest() },
    ),
    plan = caseManager?.let { this.toDPSSyncPlanRequest() },

    createdAt = LocalDateTime.parse(createDateTime),
    createdBy = createdBy,
    createdByDisplayName = createdByDisplayName ?: createdBy,
    lastModifiedAt = lastModifiedDateTime?.let { LocalDateTime.parse(lastModifiedDateTime) },
    lastModifiedBy = lastModifiedBy,
    lastModifiedByDisplayName = lastModifiedByDisplayName ?: lastModifiedBy,

    actionedAt = actioned.actionedAt,
    actionedBy = actioned.actionedBy,
    actionedByDisplayName = actioned.actionedByDisplayName,
  )

data class ActionDetails(
  val actionedAt: LocalDateTime,
  val actionedBy: String,
  val actionedByDisplayName: String,
)

fun CSIPResponse.toActionDetails() =
  ActionDetails(
    actionedAt = lastModifiedDateTime?.let { LocalDateTime.parse(lastModifiedDateTime) } ?: LocalDateTime.parse(createDateTime),
    actionedBy = lastModifiedBy ?: createdBy,
    actionedByDisplayName = lastModifiedByDisplayName ?: lastModifiedBy ?: createdByDisplayName ?: createdBy,
  )

fun SaferCustodyScreening.toDPSSyncCSIPSCS() =
  SyncScreeningOutcomeRequest(
    outcomeTypeCode = outcome!!.code,
    date = recordedDate!!,
    reasonForDecision = reasonForDecision!!,
    recordedBy = recordedBy!!,
    recordedByDisplayName = recordedByDisplayName!!,
  )

fun CSIPFactorResponse.toDPSSyncContributoryFactorRequest() =
  SyncContributoryFactorRequest(
    // TODO Add in id for update
    id = null,
    legacyId = id,
    factorTypeCode = type.code,
    comment = comment,
    createdAt = LocalDateTime.parse(createDateTime),
    createdBy = createdBy,
    // createdByDisplayName = createdByDisplayName ?: createdBy,
    createdByDisplayName = createdBy,
    lastModifiedAt = lastModifiedDateTime?.let { LocalDateTime.parse(lastModifiedDateTime) },
    lastModifiedBy = lastModifiedBy,
    // lastModifiedByDisplayName = lastModifiedByDisplayName ?: lastModifiedBy
    lastModifiedByDisplayName = lastModifiedBy,
  )

fun InvestigationDetails.toDPSSyncInvestigationRequest() =
  SyncInvestigationRequest(
    staffInvolved = staffInvolved,
    evidenceSecured = evidenceSecured,
    occurrenceReason = reasonOccurred,
    personsUsualBehaviour = usualBehaviour,
    personsTrigger = trigger,
    protectiveFactors = protectiveFactors,
    interviews = interviews?.map { it.toDPSSyncInterviewRequest() } ?: listOf(),
  ).takeUnless { isEmpty() }

fun InvestigationDetails.isEmpty() =
  staffInvolved.isNullOrEmpty() || evidenceSecured.isNullOrEmpty() || reasonOccurred.isNullOrEmpty() ||
    usualBehaviour.isNullOrEmpty() || trigger.isNullOrEmpty() || protectiveFactors.isNullOrEmpty() ||
    interviews.isNullOrEmpty()

fun InterviewDetails.toDPSSyncInterviewRequest() =
  SyncInterviewRequest(
    // TODO Add in id for update
    id = null,
    legacyId = id,
    interviewee = interviewee,
    interviewDate = date,
    intervieweeRoleCode = role.code,
    interviewText = comments,

    createdAt = LocalDateTime.parse(createDateTime),
    createdBy = createdBy,
    createdByDisplayName = createdByDisplayName ?: createdBy,
    lastModifiedAt = lastModifiedDateTime?.let { LocalDateTime.parse(lastModifiedDateTime) },
    lastModifiedBy = lastModifiedBy,
    lastModifiedByDisplayName = lastModifiedByDisplayName ?: lastModifiedBy,
  )

fun Decision.toDPSSyncDecisionsAndActionsRequest() =
  SyncDecisionAndActionsRequest(
    outcomeTypeCode = decisionOutcome!!.code,
    conclusion = conclusion,
    signedOffByRoleCode = signedOffRole?.code ?: "OTHER",
    recordedBy = recordedBy,
    recordedByDisplayName = recordedByDisplayName,
    date = recordedDate,
    nextSteps = nextSteps,
    actions = actions.toDPSSyncActions(),
    actionOther = otherDetails,
  )

fun Actions.toDPSSyncActions(): MutableSet<SyncDecisionAndActionsRequest.Actions> {
  val dpsActions: MutableSet<SyncDecisionAndActionsRequest.Actions> = mutableSetOf()
  dpsActions.addIfTrue(openCSIPAlert, SyncDecisionAndActionsRequest.Actions.OPEN_CSIP_ALERT)
  dpsActions.addIfTrue(nonAssociationsUpdated, SyncDecisionAndActionsRequest.Actions.NON_ASSOCIATIONS_UPDATED)
  dpsActions.addIfTrue(observationBook, SyncDecisionAndActionsRequest.Actions.OBSERVATION_BOOK)
  dpsActions.addIfTrue(unitOrCellMove, SyncDecisionAndActionsRequest.Actions.UNIT_OR_CELL_MOVE)
  dpsActions.addIfTrue(csraOrRsraReview, SyncDecisionAndActionsRequest.Actions.CSRA_OR_RSRA_REVIEW)
  dpsActions.addIfTrue(serviceReferral, SyncDecisionAndActionsRequest.Actions.SERVICE_REFERRAL)
  dpsActions.addIfTrue(simReferral, SyncDecisionAndActionsRequest.Actions.SIM_REFERRAL)
  return dpsActions
}

fun MutableSet<SyncDecisionAndActionsRequest.Actions>.addIfTrue(actionSet: Boolean, action: SyncDecisionAndActionsRequest.Actions) {
  if (actionSet) this.add(action)
}

fun CSIPResponse.toDPSSyncPlanRequest() =
  SyncPlanRequest(
    caseManager = caseManager!!,
    reasonForPlan = planReason!!,
    firstCaseReviewDate = firstCaseReviewDate!!,
    identifiedNeeds = plans.map { it.toDPSSyncNeedRequest() },
    reviews = reviews.map { it.toDPSSyncReviewRequest() },
  )

fun Plan.toDPSSyncNeedRequest() =
  SyncNeedRequest(
    // TODO Add in id for update
    id = null,
    legacyId = id,
    identifiedNeed = identifiedNeed,
    responsiblePerson = referredBy!!,
    intervention = intervention,
    progression = progression,

    closedDate = closedDate,
    targetDate = targetDate,

    createdAt = LocalDateTime.parse(createDateTime),
    createdBy = createdBy,
    createdByDisplayName = createdByDisplayName ?: createdBy,
    createdDate = createdDate,

    lastModifiedAt = lastModifiedDateTime?.let { LocalDateTime.parse(lastModifiedDateTime) },
    lastModifiedBy = lastModifiedBy,
    lastModifiedByDisplayName = lastModifiedByDisplayName ?: lastModifiedBy,
  )

fun Review.toDPSSyncReviewRequest() =
  SyncReviewRequest(
    // TODO Add in id for update
    id = null,
    legacyId = id,
    recordedBy = recordedBy,
    recordedByDisplayName = recordedByDisplayName!!,
    reviewDate = recordedDate,
    summary = summary,
    nextReviewDate = nextReviewDate,
    csipClosedDate = closeDate,

    actions = toSyncReviewActions(),
    attendees = attendees.map { it.toSyncAttendeeRequest() },

    createdAt = LocalDateTime.parse(createDateTime),
    createdBy = createdBy,
    createdByDisplayName = createdByDisplayName ?: createdBy,

    lastModifiedAt = lastModifiedDateTime?.let { LocalDateTime.parse(lastModifiedDateTime) },
    lastModifiedBy = lastModifiedBy,
    lastModifiedByDisplayName = lastModifiedByDisplayName ?: lastModifiedBy,
  )

fun Review.toSyncReviewActions(): MutableSet<SyncReviewRequest.Actions> {
  val dpsActions: MutableSet<SyncReviewRequest.Actions> = mutableSetOf()
  dpsActions.addIfTrue(peopleInformed, SyncReviewRequest.Actions.RESPONSIBLE_PEOPLE_INFORMED)
  dpsActions.addIfTrue(csipUpdated, SyncReviewRequest.Actions.CSIP_UPDATED)
  dpsActions.addIfTrue(remainOnCSIP, SyncReviewRequest.Actions.REMAIN_ON_CSIP)
  dpsActions.addIfTrue(caseNote, SyncReviewRequest.Actions.CASE_NOTE)
  dpsActions.addIfTrue(closeCSIP, SyncReviewRequest.Actions.CLOSE_CSIP)
  return dpsActions
}
fun MutableSet<SyncReviewRequest.Actions>.addIfTrue(actionSet: Boolean, action: SyncReviewRequest.Actions) {
  if (actionSet) this.add(action)
}
fun Attendee.toSyncAttendeeRequest() =
  SyncAttendeeRequest(
    // TODO Add in for update
    id = null,
    legacyId = id,

    name = name,
    role = role,
    isAttended = attended,
    contribution = contribution,

    createdAt = LocalDateTime.parse(createDateTime),
    createdBy = createdBy,
    createdByDisplayName = createdByDisplayName ?: createdBy,

    lastModifiedAt = lastModifiedDateTime?.let { LocalDateTime.parse(lastModifiedDateTime) },
    lastModifiedBy = lastModifiedBy,
    lastModifiedByDisplayName = lastModifiedByDisplayName ?: lastModifiedBy,
  )