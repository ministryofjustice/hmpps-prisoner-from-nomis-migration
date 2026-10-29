package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.personlocation

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.TelemetryEnabled
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.track
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.tryFetchParent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.ExternalMovementEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.MovementType.TRN
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.toDpsUser
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.transfer.TRN_URN_PREFIX
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.transfer.TransferScheduleMappingApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonLocationMovementIdMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonLocationMovementIdMappingDto.MappingType.NOMIS_CREATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PersonLocationMovement
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personlocation.model.SyncExternalMovementRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personlocation.model.SyncUser
import java.util.UUID

private const val TELEMETRY_PREFIX = "person-location-sync-movement"

@Service
class PersonLocationSyncMovementService(
  override val telemetryClient: TelemetryClient,
  private val nomisApiService: PersonLocationNomisApiService,
  private val mappingApiService: PersonLocationMappingApiService,
  private val dpsApiService: PersonLocationDpsApiService,
  private val transferScheduleMappingApiService: TransferScheduleMappingApiService,
) : TelemetryEnabled {

  suspend fun externalMovementChanged(event: ExternalMovementEvent) {
    // Only inserted transfer movements are synchronised so far
    if (event.movementType == TRN && event.recordInserted) {
      externalMovementInserted(event)
    }
  }

  private suspend fun externalMovementInserted(event: ExternalMovementEvent) {
    val (bookingId, prisonerNumber, movementSeq) = event
    val telemetry = mutableMapOf<String, Any>(
      "offenderNo" to prisonerNumber!!,
      "bookingId" to bookingId,
      "movementSeq" to movementSeq,
    )

    if (mappingApiService.getMovementMappingOrNull(bookingId, movementSeq) != null) return

    track("$TELEMETRY_PREFIX-inserted", telemetry) {
      val nomis = nomisApiService.getPersonLocationMovement(prisonerNumber, bookingId, movementSeq)
        .also { telemetry["nomisEventId"] = "${it.scheduleId}" }
      val dpsScheduleId = nomis.scheduleId?.let { scheduleId ->
        tryFetchParent { transferScheduleMappingApiService.getTransferScheduleMappingOrNull(scheduleId) }
          .dpsTransferScheduleId
      }
      val dpsCustodialSeriesId = tryFetchParent { mappingApiService.getBookingMappingOrNull(bookingId) }
        .dpsCustodialSeriesId

      val dps = dpsApiService.syncExternalMovement(
        prisonerNumber,
        nomis.toDpsRequest(dpsCustodialSeriesId, dpsScheduleId?.let { "$TRN_URN_PREFIX$it" }),
      ).also { telemetry["dpsExternalMovementId"] = it.dpsId }

      mappingApiService.createMovementMapping(
        PersonLocationMovementIdMappingDto(
          nomisBookingId = bookingId,
          nomisMovementSeq = movementSeq,
          dpsExternalMovementId = dps.dpsId,
          mappingType = NOMIS_CREATED,
        ),
      )
    }
  }
}

private fun PersonLocationMovement.toDpsRequest(dpsCustodialSeriesId: UUID, scheduleReference: String?) = SyncExternalMovementRequest(
  occurredAt = audit.modifyDatetime ?: audit.createDatetime,
  syncUser = SyncUser(username = (audit.modifyUserId ?: audit.createUsername).toDpsUser()),
  movement = toSyncExternalMovement(dpsId = null, dpsCustodySeriesId = dpsCustodialSeriesId, scheduleReference = scheduleReference),
)
