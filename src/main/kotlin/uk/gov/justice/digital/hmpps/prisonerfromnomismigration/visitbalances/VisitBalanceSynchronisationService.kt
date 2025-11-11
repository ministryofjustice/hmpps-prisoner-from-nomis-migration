package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visitbalances

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.TelemetryEnabled
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.originatesInDpsOrHasMissingAudit
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.telemetryOf
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.track
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.valuesAsStrings
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.SynchronisationMessageType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.VisitBalanceAdjustmentMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.VisitBalanceAdjustmentResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.VisitBalanceDetailResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.InternalMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visit.balance.model.VisitAllocationPrisonerSyncDto

@Service
class VisitBalanceSynchronisationService(
  private val nomisVisitBalanceApiService: VisitBalanceNomisApiService,
  private val nomisApiService: NomisApiService,
  private val dpsApiService: VisitBalanceDpsApiService,
  private val mappingApiService: VisitBalanceMappingApiService,
  private val queueService: SynchronisationQueueService,
  override val telemetryClient: TelemetryClient,
) : TelemetryEnabled {
  private companion object {
    const val VISIT_ALLOCATION_SERVICE = "VISIT_ALLOCATION"
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun visitBalanceAdjustmentInserted(event: VisitBalanceOffenderEvent) {
    val visitBalanceAdjustmentId = event.visitBalanceAdjustmentId
    val nomisPrisonNumber = event.offenderIdDisplay
    val telemetry =
      telemetryOf("nomisVisitBalanceAdjustmentId" to visitBalanceAdjustmentId, "nomisPrisonNumber" to nomisPrisonNumber)

    if (event.originatesInDpsOrHasMissingAudit() &&
      nomisApiService.isServiceAgencyOnForPrisoner(
        serviceCode = VISIT_ALLOCATION_SERVICE,
        prisonNumber = nomisPrisonNumber,
      )
    ) {
      telemetryClient.trackEvent(
        "visitbalance-adjustment-synchronisation-created-skipped",
        telemetry,
      )
    } else {
      nomisVisitBalanceApiService.getVisitBalanceAdjustment(visitBalanceAdjustmentId = visitBalanceAdjustmentId).also {
        if (!it.latestBooking) {
          telemetryClient.trackEvent(
            "visitbalance-adjustment-synchronisation-old-booking-ignored",
            telemetry,
          )
        } else {
          val mapping = mappingApiService.getByNomisVisitBalanceAdjustmentIdOrNull(visitBalanceAdjustmentId)
          if (mapping != null) {
            telemetryClient.trackEvent(
              "visitbalance-adjustment-synchronisation-created-ignored",
              telemetry,
            )
          } else {
            // special case for Initial IEP entitlement
            if (it.comment == "Initial IEP entitlement") {
              val visitBalance = nomisVisitBalanceApiService.getVisitBalanceDetail(event.bookingId)
              dpsApiService.migrateVisitBalance(visitBalance.toMigrationDto())
              telemetryClient.trackEvent(
                "visitbalance-adjustment-synchronisation-balance-success",
                telemetry,
              )
            } else {
              telemetry += telemetryOf(
                "visitOrderChange" to it.visitOrderChange.toString(),
                "previousVisitOrderCount" to it.previousVisitOrderCount.toString(),
                "privilegedVisitOrderChange" to it.privilegedVisitOrderChange.toString(),
                "previousPrivilegedVisitOrderCount" to it.previousPrivilegedVisitOrderCount.toString(),
              )

              track("visitbalance-adjustment-synchronisation-created", telemetry) {
                dpsApiService.syncVisitBalanceAdjustment(it.toSyncDto(nomisPrisonNumber))
                val mapping = VisitBalanceAdjustmentMappingDto(
                  nomisVisitBalanceAdjustmentId = visitBalanceAdjustmentId,
                  dpsId = event.offenderIdDisplay,
                  mappingType = VisitBalanceAdjustmentMappingDto.MappingType.NOMIS_CREATED,
                )
                tryToCreateMapping(mapping, telemetry)
              }
            }
          }
        }
      }
    }
  }

  suspend fun visitBalanceAdjustmentDeleted(event: VisitBalanceOffenderEvent) {
    val telemetry = telemetryOf(
      "nomisVisitBalanceAdjustmentId" to event.visitBalanceAdjustmentId,
      "nomisPrisonNumber" to event.offenderIdDisplay,
    )
    telemetryClient.trackEvent("visitbalance-adjustment-synchronisation-deleted-unexpected", telemetry)
  }

  suspend fun resynchroniseVisitBalance(prisonNumber: String) {
    val visitBalance =
      nomisVisitBalanceApiService.getVisitBalanceDetailForPrisoner(prisonNumber) ?: VisitBalanceDetailResponse(
        prisonNumber,
        0,
        0,
      )
    dpsApiService.migrateVisitBalance(visitBalance.toMigrationDto())
  }

  private suspend fun tryToCreateMapping(
    mapping: VisitBalanceAdjustmentMappingDto,
    telemetry: Map<String, Any>,
  ) {
    try {
      mappingApiService.createVisitBalanceAdjustmentMapping(mapping).takeIf { it.isError }?.also {
        with(it.errorResponse!!.moreInfo) {
          telemetryClient.trackEvent(
            "visitbalance-adjustment-duplicate",
            mapOf(
              "existingNomisVisitBalanceAdjustmentId" to existing.nomisVisitBalanceAdjustmentId,
              "existingDpsId" to existing.dpsId,
              "duplicateNomisVisitBalanceAdjustmentId" to duplicate.nomisVisitBalanceAdjustmentId,
              "duplicateDpsId" to duplicate.dpsId,
            ),
          )
        }
      }
    } catch (e: Exception) {
      log.error("Failed to create mapping for visit balance adjustment id $mapping", e)
      queueService.sendMessage(
        messageType = SynchronisationMessageType.RETRY_SYNCHRONISATION_MAPPING.name,
        synchronisationType = SynchronisationType.VISIT_BALANCE,
        message = mapping,
        telemetryAttributes = telemetry.valuesAsStrings(),
      )
    }
  }

  suspend fun retryCreateMapping(retryMessage: InternalMessage<VisitBalanceAdjustmentMappingDto>) {
    mappingApiService.createVisitBalanceAdjustmentMapping(
      retryMessage.body,
    ).also {
      telemetryClient.trackEvent(
        "visitbalance-adjustment-mapping-created-synchronisation-success",
        retryMessage.telemetryAttributes,
      )
    }
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
