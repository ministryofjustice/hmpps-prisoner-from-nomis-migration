package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.advances

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.FinanceDpsApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.MoneySupport
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.model.SyncCreateAdvanceRecordRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerAdvanceDto

@Service
class PrisonerAdvanceSynchronisationService(
  private val nomisApiService: AdvancesNomisApiService,
  private val dpsApiService: FinanceDpsApiService,
) {
  /* TODO
  suspend fun resynchronisePrisonerAdvance(advanceId: Long) {
    val advance = nomisApiService.getAdvance(advanceId)
      ?: throw NotFoundException("advanceId $advanceId not found")
    dpsApiService.syncPrisonerAdvance(advance.toSyncAdvanceDto())
  }
   */
}

fun PrisonerAdvanceDto.toSyncAdvanceDto() = SyncCreateAdvanceRecordRequest(
  legacyPaymentProfileId = id,
  prisonNumber = prisonNumber,
  prisonID = caseloadId,
  amount = MoneySupport.penceToPounds(advanceAmount),
  repaymentStartDate = startDate.atStartOfDay(),
  repaymentAmount = MoneySupport.penceToPounds(repaymentAmount),
  // TODO remove the !!
  reference = reference!!,
  // TODO
  // comment = comment,
  // TODO
  status = SyncCreateAdvanceRecordRequest.Status.ACTIVE,
  // TODO - do we need this?
  // advanceDate = LocalDate.now(),
  createdOn = createDatetime,
  createdBy = createdBy,
  // TODO
  legacyInformationNumber = "1234",
)
