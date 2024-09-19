package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip

import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.CreateContributoryFactorRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.CreateSaferCustodyScreeningOutcomeRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.UpdateContributoryFactorRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.UpdateCsipRecordRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.UpdateInvestigationRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.UpdatePlanRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.UpdateReferral
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.UpdateReferral.IsSaferCustodyTeamInformed.NO
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.UpdateReferral.IsSaferCustodyTeamInformed.YES
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.UpsertDecisionAndActionsRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.Actions
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CSIPFactorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CSIPResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.Decision
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.InvestigationDetails
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.SaferCustodyScreening

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
    date = recordedDate!!,
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

fun InvestigationDetails.toDPSUpdateInvestigationRequest() =
  UpdateInvestigationRequest(
    staffInvolved = staffInvolved,
    evidenceSecured = evidenceSecured,
    occurrenceReason = reasonOccurred,
    personsUsualBehaviour = usualBehaviour,
    personsTrigger = trigger,
    protectiveFactors = protectiveFactors,
  )

// ////// OIDCSIPD - Decisions & Actions ////////////////////////////
fun Decision.toDPSUpsertDecisionsAndActionsRequest() =
  UpsertDecisionAndActionsRequest(
    outcomeTypeCode = decisionOutcome!!.code,
    conclusion = conclusion,
    signedOffByRoleCode = signedOffRole?.code ?: "OTHER",
    recordedBy = recordedBy,
    recordedByDisplayName = recordedByDisplayName,
    date = recordedDate,
    nextSteps = nextSteps,
    actions = actions.toUpsertDPSActions(),
    actionOther = otherDetails,
  )

fun Actions.toUpsertDPSActions(): MutableSet<UpsertDecisionAndActionsRequest.Actions> {
  val dpsActions: MutableSet<UpsertDecisionAndActionsRequest.Actions> = mutableSetOf()
  dpsActions.addIfTrue(openCSIPAlert, UpsertDecisionAndActionsRequest.Actions.OPEN_CSIP_ALERT)
  dpsActions.addIfTrue(nonAssociationsUpdated, UpsertDecisionAndActionsRequest.Actions.NON_ASSOCIATIONS_UPDATED)
  dpsActions.addIfTrue(observationBook, UpsertDecisionAndActionsRequest.Actions.OBSERVATION_BOOK)
  dpsActions.addIfTrue(unitOrCellMove, UpsertDecisionAndActionsRequest.Actions.UNIT_OR_CELL_MOVE)
  dpsActions.addIfTrue(csraOrRsraReview, UpsertDecisionAndActionsRequest.Actions.CSRA_OR_RSRA_REVIEW)
  dpsActions.addIfTrue(serviceReferral, UpsertDecisionAndActionsRequest.Actions.SERVICE_REFERRAL)
  dpsActions.addIfTrue(simReferral, UpsertDecisionAndActionsRequest.Actions.SIM_REFERRAL)
  return dpsActions
}

fun MutableSet<UpsertDecisionAndActionsRequest.Actions>.addIfTrue(actionSet: Boolean, action: UpsertDecisionAndActionsRequest.Actions) {
  if (actionSet) this.add(action)
}

// ////// OIDCSIPP - Plan ////////////////////////////
fun CSIPResponse.toDPSUpsertPlanRequest() =
  UpdatePlanRequest(
    caseManager = caseManager!!,
    reasonForPlan = planReason!!,
    firstCaseReviewDate = firstCaseReviewDate!!,
  )
