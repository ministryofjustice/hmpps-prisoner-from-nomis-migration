package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.transfer

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.TelemetryEnabled
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.track
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.ExternalMovementEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.MovementType.TRN
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.toDpsUser
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TransferMovementMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TransferMovementMappingDto.MappingType.NOMIS_CREATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.TransferMovementOut
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.transferschedule.model.SyncMovement
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.transferschedule.model.SyncMovementRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.transferschedule.model.SyncUser
import java.util.*

private const val TELEMETRY_PREFIX: String = "${TRANSFER_TELEMETRY_PREFIX}-movement"

@Service
class TransferScheduleSyncMovementService(
  override val telemetryClient: TelemetryClient,
  private val mappingApiService: TransferScheduleMappingApiService,
  private val nomisApiService: TransferScheduleNomisApiService,
  private val dpsApiService: TransferScheduleDpsApiService,
) : TelemetryEnabled {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun transferMovementChanged(event: ExternalMovementEvent) = when {
    event.movementType != TRN -> {}
    event.recordInserted -> transferMovementInserted(event)
    else -> {}
  }

  suspend fun transferMovementInserted(event: ExternalMovementEvent) {
    val (bookingId, prisonerNumber, movementSeq) = event
    val telemetry = mutableMapOf<String, Any>(
      "offenderNo" to prisonerNumber!!,
      "bookingId" to bookingId,
      "movementSeq" to movementSeq,
    )

    mappingApiService.getTransferMovementMappingOrNull(bookingId, movementSeq)
      ?: run {
        track("${TELEMETRY_PREFIX}-inserted", telemetry) {
          val nomis = nomisApiService.getTransferMovementOut(prisonerNumber, bookingId, movementSeq)
            .also { telemetry["nomisEventId"] = "${it.transferScheduleOutId}" }
          val scheduleMapping = nomis.transferScheduleOutId?.let { mappingApiService.getTransferScheduleMappingOrNull(it) }
            ?.also { telemetry["dpsTransferScheduleId"] = it.dpsTransferScheduleId }
          val dps = dpsApiService.syncTransferMovement(prisonerNumber, nomis.toDpsRequest(dpsTransferScheduleId = scheduleMapping?.dpsTransferScheduleId))
            .also { telemetry["dpsTransferMovementId"] = it.dpsId }
          mappingApiService.createTransferMovementMapping(
            TransferMovementMappingDto(
              prisonerNumber,
              bookingId,
              movementSeq,
              dps.dpsId,
              NOMIS_CREATED,
            ),
          )
        }
      }
  }
}

private fun TransferMovementOut.toDpsRequest(dpsId: UUID? = null, dpsTransferScheduleId: UUID? = null) = SyncMovementRequest(
  movement = SyncMovement(
    occurredAt = movementTime,
    movementReasonCode = movementReason,
    escortCode = escort ?: DEFAULT_ESCORT_CODE,
    fromAgyLocId = fromPrison,
    toAgyLocId = toPrison,
    dpsId = dpsId,
    dpsTransferId = dpsTransferScheduleId,
    offenderBookId = bookingId,
    movementSeq = sequence,
    active = active,
    commentText = commentText,
  ),
  syncUser = SyncUser(
    username = (audit.modifyDisplayName ?: audit.createUsername).toDpsUser(),
    activeCaseloadId = userActiveCaseloadId,
  ),
  occurredAt = audit.modifyDatetime ?: audit.createDatetime,
)
