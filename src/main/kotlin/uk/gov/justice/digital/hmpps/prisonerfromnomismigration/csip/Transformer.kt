package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip

import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.CreateContributoryFactorRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.CreateCsipRecordRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.CreateReferralRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.CreateSaferCustodyScreeningOutcomeRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.UpdateContributoryFactorRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.UpdateReferralRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CSIPFactorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CSIPResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.SaferCustodyScreening

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
      referralSummary = null,
      isSaferCustodyTeamInformed = false,
      isReferralComplete = true,

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
    ),
  )

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
  )

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
    isSaferCustodyTeamInformed = reportDetails.saferCustodyTeamInformed,
    isReferralComplete = reportDetails.referralComplete,
  )

fun SaferCustodyScreening.toDPSCreateCSIPSCS() =
  CreateSaferCustodyScreeningOutcomeRequest(
    outcomeTypeCode = outcome!!.code,
    date = this.recordedDate!!,
    reasonForDecision = reasonForDecision!!,
  )

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
