package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.advances

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.AdvanceEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.FinanceDpsApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.MoneySupport
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.model.SyncCreateAdvanceRecordRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.TelemetryEnabled
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.telemetryOf
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.track
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerAdvanceDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NotFoundException

@Service
class AdvancesSynchronisationService(
  private val nomisApiService: AdvancesNomisApiService,
  private val dpsApiService: FinanceDpsApiService,
  override val telemetryClient: TelemetryClient,
) : TelemetryEnabled {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun resynchroniseAdvance(advanceId: Long) {
    val advance = nomisApiService.getAdvance(advanceId)
      ?: throw NotFoundException("advanceId $advanceId not found")
    dpsApiService.createAdvance(advance.toSyncAdvanceDto())
  }

  suspend fun advanceInserted(event: AdvanceEvent) {
    val advanceId = event.offenderAdvanceId
    val telemetry = telemetryOf("nomisAdvanceId" to advanceId, "prisonNumber" to event.offenderIdDisplay)

    if (event.originatesInDpsOrHasMissingAudit) {
      telemetryClient.trackEvent("prisoneradvance-synchronisation-created-skipped", telemetry)
    } else {
      track("prisoneradvance-synchronisation-created", telemetry) {
        val advance = nomisApiService.getAdvance(advanceId)
          ?: throw NotFoundException("advanceId $advanceId not found")
        dpsApiService.createAdvance(advance.toSyncAdvanceDto())
      }
    }
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
  // TODO this should actually always be set - even though nullable in nomis prisoner api it is always set in db
  legacyInformationNumber = informationNumber!!,
  // TODO pull in from Nomis
  legacyTransactionId = 123,
)
