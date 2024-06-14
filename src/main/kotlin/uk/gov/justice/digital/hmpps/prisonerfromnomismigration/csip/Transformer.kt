package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip

import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.CreateCsipRecordRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.CreateReferralRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CSIPResponse
import java.time.LocalDateTime

fun CSIPResponse.toDPSCreateCSIP() =
  CreateCsipRecordRequest(
    // TODO Waiting for csip update as this can be null
    logNumber = logNumber!!,
    referral =
    CreateReferralRequest(
      incidentDate = LocalDateTime.parse(incidentDateTime).toLocalDate(),
      incidentTypeCode = type.code,
      incidentLocationCode = location.code,
      // TODO Waiting for csip update as this can be null
      referredBy = reportedBy!!,
      refererAreaCode = areaOfWork.code,
      // TODO Waiting for csip update as this can be null
      incidentInvolvementCode = reportDetails.involvement!!.code,
      // TODO Waiting for csip update as this can be null
      descriptionOfConcern = reportDetails.concern!!,
      // TODO Waiting for csip update as this can be null
      knownReasons = reportDetails.knownReasons!!,
      // TODO Add factors in
      contributoryFactors = listOf(),
      incidentTime = LocalDateTime.parse(incidentDateTime).toLocalTime()?.toString(),
      // TODO
      referralSummary = null,
      isProactiveReferral = proActiveReferral,
      isStaffAssaulted = staffAssaulted,
      assaultedStaffName = staffAssaultedName,
      otherInformation = reportDetails.otherInformation,
      isSaferCustodyTeamInformed = reportDetails.saferCustodyTeamInformed,
      isReferralComplete = reportDetails.referralComplete,
    ),
  )
