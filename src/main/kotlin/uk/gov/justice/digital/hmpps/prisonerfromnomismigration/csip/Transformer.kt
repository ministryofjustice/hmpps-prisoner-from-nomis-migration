package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip

import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.CreateCsipRecordRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.CreateReferralRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.CreateSaferCustodyScreeningOutcomeRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CSIPResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.SaferCustodyScreening
import java.time.LocalDateTime

fun CSIPResponse.toDPSCreateCSIP() =
  CreateCsipRecordRequest(
    // TODO Waiting for csip update as this can be null
    logNumber = logNumber!!,
    referral =
    CreateReferralRequest(
      incidentDate = LocalDateTime.parse(incidentDateTime).toLocalDate(),
      incidentTime = LocalDateTime.parse(incidentDateTime).toLocalTime()?.toString(),
      incidentTypeCode = type.code,
      incidentLocationCode = location.code,
      // TODO Waiting for csip update as this can be null - it can't thru the UI!!!
      referredBy = reportedBy!!,
      refererAreaCode = areaOfWork.code,

      isProactiveReferral = proActiveReferral,
      isStaffAssaulted = staffAssaulted,
      assaultedStaffName = staffAssaultedName,

      // No other fields can be set here in Nomis from the first page

      // TODO TIDY THESE FIELDS WHEN csip api code updated
      // referralSummary = null,
      // TODO Waiting for csip update as this can be null
      incidentInvolvementCode = reportDetails.involvement!!.code,
      // TODO Waiting for csip update as this can be null
      descriptionOfConcern = reportDetails.concern!!,
      // TODO Waiting for csip update as this can be null
      knownReasons = reportDetails.knownReasons!!,
      // TODO Add factors in
      contributoryFactors = listOf(),
      // otherInformation = null,
      // isSaferCustodyTeamInformed = null,
      // isReferralComplete = null,
    ),
  )

fun SaferCustodyScreening.toDPSCreateCSIPSCS() =
  CreateSaferCustodyScreeningOutcomeRequest(
    outcomeTypeCode = outcome!!.code,
    date = this.recordedDate!!,
    reasonForDecision = reasonForDecision!!,
  )
