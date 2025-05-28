package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visitbalances

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.telemetryOf
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.VisitBalanceAdjustmentResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visit.balance.model.VisitAllocationPrisonerSyncDto

@Service
class VisitBalanceSynchronisationService(
  private val nomisVisitBalanceApiService: VisitBalanceNomisApiService,
  private val nomisApiService: NomisApiService,
  private val dpsApiService: VisitBalanceDpsApiService,
  private val telemetryClient: TelemetryClient,
) {
  private companion object {
    const val VISIT_ALLOCATION_SERVICE = "VISIT_ALLOCATION"
  }

  suspend fun visitBalanceAdjustmentInserted(event: VisitBalanceOffenderEvent) {
    val visitBalanceAdjustmentId = event.visitBalanceAdjustmentId
    val nomisPrisonNumber = event.offenderIdDisplay
    val telemetry = telemetryOf("visitBalanceAdjustmentId" to visitBalanceAdjustmentId, "nomisPrisonNumber" to nomisPrisonNumber)

    if (event.originatesInDpsOrHasMissingAudit() && nomisApiService.isServicePrisonOnForPrisoner(serviceCode = VISIT_ALLOCATION_SERVICE, prisonNumber = nomisPrisonNumber)) {
      telemetryClient.trackEvent(
        "visitbalance-adjustment-synchronisation-created-skipped",
        telemetry,
      )
    } else {
      nomisVisitBalanceApiService.getVisitBalanceAdjustment(visitBalanceAdjustmentId = visitBalanceAdjustmentId).also {
        dpsApiService.syncVisitBalanceAdjustment(it.toSyncDto(nomisPrisonNumber))
        telemetryClient.trackEvent(
          "visitbalance-adjustment-synchronisation-created-success",
          telemetry,
        )
      }
    }
  }

  suspend fun visitBalanceAdjustmentDeleted(event: VisitBalanceOffenderEvent) {
    val telemetry = telemetryOf(
      "visitBalanceAdjustmentId" to event.visitBalanceAdjustmentId,
      "nomisPrisonNumber" to event.offenderIdDisplay,
    )
    telemetryClient.trackEvent("visitbalance-adjustment-synchronisation-deleted-unexpected", telemetry)
  }
}

fun VisitBalanceAdjustmentResponse.toSyncDto(nomisPrisonNumber: String) = VisitAllocationPrisonerSyncDto(
  prisonerId = nomisPrisonNumber,
  oldVoBalance = previousVisitOrderCount,
  changeToVoBalance = visitOrderChange,
  oldPvoBalance = previousPrivilegedVisitOrderCount,
  changeToPvoBalance = privilegedVisitOrderChange,
  createdDate = adjustmentDate,
  adjustmentReasonCode = VisitAllocationPrisonerSyncDto.AdjustmentReasonCode.valueOf(adjustmentReason.code),
  changeLogSource = if (createUsername == "OMS_OWNER") VisitAllocationPrisonerSyncDto.ChangeLogSource.SYSTEM else VisitAllocationPrisonerSyncDto.ChangeLogSource.STAFF,
  comment = comment,
)

fun VisitBalanceOffenderEvent.originatesInDpsOrHasMissingAudit() = auditModuleName == "DPS_SYNCHRONISATION" || auditModuleName.isNullOrEmpty()
