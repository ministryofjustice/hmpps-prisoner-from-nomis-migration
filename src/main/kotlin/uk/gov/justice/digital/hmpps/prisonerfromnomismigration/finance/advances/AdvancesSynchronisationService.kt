package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.advances

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.FinanceDpsApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.MoneySupport
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.model.SyncCreateAdvanceRecordRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerAdvanceDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NotFoundException

@Service
class PrisonerAdvanceSynchronisationService(
  private val nomisApiService: AdvancesNomisApiService,
  private val dpsApiService: FinanceDpsApiService,
) {
  suspend fun resynchronisePrisonerAdvance(advanceId: Long) {
    val advance = nomisApiService.getAdvance(advanceId)
      ?: throw NotFoundException("advanceId $advanceId not found")
    dpsApiService.syncPrisonerAdvance(advance.toSyncAdvanceDto())
  }
}

fun PrisonerAdvanceDto.toSyncAdvanceDto() = SyncCreateAdvanceRecordRequest(
  legacyPaymentProfileId = id,
  prisonNumber = prisonNumber,
  prisonID = caseloadId,
  amount = MoneySupport.penceToPounds(advanceAmount),
  repaymentStartDate = startDate.atStartOfDay(),
  repaymentAmount = MoneySupport.penceToPounds(repaymentAmount),
  reference = reference,
  comment = comment,
  // TODO pull in from Nomis
  status = SyncCreateAdvanceRecordRequest.Status.ACTIVE,
  createdOn = createDatetime,
  createdBy = createdBy,
  // TODO pull in from Nomis
  legacyInformationNumber = "1234",
  // TODO pull in from Nomis
  legacyTransactionId = 123,
)
