package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip

import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.CreateContributoryFactorRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.CreateCsipRecordRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.CreateReferralRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.CreateSaferCustodyScreeningOutcomeRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CSIPFactorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CSIPResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.SaferCustodyScreening
import java.time.LocalDateTime

fun CSIPResponse.toDPSMigrateCSIP() =
  CreateCsipRecordRequest(
    logNumber = logNumber,
    referral =
    CreateReferralRequest(
      incidentDate = LocalDateTime.parse(incidentDateTime).toLocalDate(),
      incidentTime = LocalDateTime.parse(incidentDateTime).toLocalTime()?.toString(),
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

fun CSIPResponse.toDPSCreateCSIP() =
  CreateCsipRecordRequest(
    logNumber = logNumber,
    referral = CreateReferralRequest(
      incidentDate = LocalDateTime.parse(incidentDateTime).toLocalDate(),
      incidentTime = LocalDateTime.parse(incidentDateTime).toLocalTime()?.toString(),
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

fun SaferCustodyScreening.toDPSCreateCSIPSCS() =
  CreateSaferCustodyScreeningOutcomeRequest(
    outcomeTypeCode = outcome!!.code,
    date = this.recordedDate!!,
    reasonForDecision = reasonForDecision!!,
  )

fun CSIPFactorResponse.toDPSFactorRequest() =
  CreateContributoryFactorRequest(
    factorTypeCode = type.code,
    comment = comment,
  )
