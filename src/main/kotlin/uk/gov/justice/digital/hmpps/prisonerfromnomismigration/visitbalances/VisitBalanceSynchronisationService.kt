package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visitbalances

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.PrisonerBookingMovedDomainEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.doesOriginateInDps
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.telemetryOf
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.VisitBalanceAdjustmentResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visit.balance.model.VisitAllocationPrisonerSyncDto

@Service
class VisitBalanceSynchronisationService(
  private val nomisApiService: VisitBalanceNomisApiService,
  private val dpsApiService: VisitBalanceDpsApiService,
  private val telemetryClient: TelemetryClient,
) {
  suspend fun visitBalanceAdjustmentInserted(event: VisitBalanceOffenderEvent) {
    val visitBalanceAdjustmentId = event.visitBalanceAdjustmentId
    val nomisPrisonNumber = event.offenderIdDisplay
    val telemetry = telemetryOf("visitBalanceAdjustmentId" to visitBalanceAdjustmentId, "nomisPrisonNumber" to nomisPrisonNumber)
    if (event.doesOriginateInDps()) {
      telemetryClient.trackEvent(
        "visitbalance-adjustment-synchronisation-created-skipped",
        telemetry,
      )
    } else {
      nomisApiService.getVisitBalanceAdjustment(visitBalanceAdjustmentId = visitBalanceAdjustmentId).also {
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

  suspend fun synchronisePrisonerBookingMoved(bookingMovedEvent: PrisonerBookingMovedDomainEvent) {
    val fromMovePrisoner = bookingMovedEvent.additionalInformation.movedFromNomsNumber
    val toMovePrisoner = bookingMovedEvent.additionalInformation.movedToNomsNumber

    val visitBalance1 = nomisApiService.getVisitBalanceForPrisoner(fromMovePrisoner)
    val visitBalance2 = nomisApiService.getVisitBalanceForPrisoner(toMovePrisoner)
    dpsApiService.syncVisitBalances(
      VisitAllocationPrisonerSyncBookingDto(
        firstPrisonerId = fromMovePrisoner,
        firstPrisonerVoBalance = visitBalance1.remainingVisitOrders,
        firstPrisonerPvoBalance = visitBalance1.remainingPrivilegedVisitOrders,

        secondPrisonerId = toMovePrisoner,
        secondPrisonerVoBalance = visitBalance2.remainingVisitOrders,
        secondPrisonerPvoBalance = visitBalance2.remainingPrivilegedVisitOrders,
      ),
    )
    val telemetry = telemetryOf(
      "bookingId" to bookingMovedEvent.additionalInformation.bookingId,
      "movedFromNomsNumber" to fromMovePrisoner,
      "movedToNomsNumber" to toMovePrisoner,
    )
    telemetryClient.trackEvent("visitbalance-adjustment-synchronisation-booking-moved", telemetry)

    // TODO do we need to try/catch and log if error?
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
