package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.personlocation

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.EventAudited
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.EventAudited.Companion.DPS_SYNC_AUDIT_MODULE
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.TelemetryEnabled
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.track
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.toDpsUser
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonLocationBookingIdMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonLocationBookingIdMappingDto.MappingType.NOMIS_CREATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PersonLocationBooking
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personlocation.model.SyncCustodialSeries
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personlocation.model.SyncCustodialSeriesRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personlocation.model.SyncUser
import java.util.UUID

private const val TELEMETRY_PREFIX = "person-location-sync-booking"

data class BookingStatusEvent(
  val bookingId: Long,
  val offenderIdDisplay: String,
  val bookingSeq: Int,
  val oldBookingSeq: Int?,
  override val auditModuleName: String?,
) : EventAudited

@Service
class PersonLocationSyncBookingService(
  override val telemetryClient: TelemetryClient,
  private val nomisApiService: PersonLocationNomisApiService,
  private val mappingApiService: PersonLocationMappingApiService,
  private val dpsApiService: PersonLocationDpsApiService,
) : TelemetryEnabled {

  suspend fun bookingInserted(event: BookingStatusEvent) {
    val (bookingId, prisonerNumber, bookingSeq) = event
    val telemetry = mutableMapOf<String, Any>(
      "offenderNo" to prisonerNumber,
      "bookingId" to bookingId,
      "bookingSeq" to bookingSeq,
    )

    if (event.auditExactMatchOrHasMissingAudit(DPS_SYNC_AUDIT_MODULE)) {
      telemetryClient.trackEvent("$TELEMETRY_PREFIX-inserted-ignored", telemetry)
      return
    }

    mappingApiService.getBookingMappingOrNull(bookingId)?.also {
      telemetry["dpsCustodialSeriesId"] = it.dpsCustodialSeriesId
      telemetryClient.trackEvent("$TELEMETRY_PREFIX-inserted-ignored", telemetry)
      return
    }

    track("$TELEMETRY_PREFIX-inserted", telemetry) {
      val nomis = nomisApiService.getPersonLocationBooking(prisonerNumber, bookingId)
      val dps = dpsApiService.syncCustodialSeries(prisonerNumber, nomis.toDpsRequest(dpsId = null))
        .also { telemetry["dpsCustodialSeriesId"] = it.dpsId }

      mappingApiService.createBookingMapping(
        PersonLocationBookingIdMappingDto(
          nomisBookingId = bookingId,
          dpsCustodialSeriesId = dps.dpsId,
          mappingType = NOMIS_CREATED,
        ),
      )
    }
  }
}

private fun PersonLocationBooking.toDpsRequest(dpsId: UUID?) = SyncCustodialSeriesRequest(
  occurredAt = audit.modifyDatetime ?: audit.createDatetime,
  syncUser = SyncUser(username = (audit.modifyUserId ?: audit.createUsername).toDpsUser()),
  custodialSeries = SyncCustodialSeries(
    dpsId = dpsId,
    status = when (bookingStatus) {
      PersonLocationBooking.BookingStatus.OPEN -> SyncCustodialSeries.Status.OPEN
      PersonLocationBooking.BookingStatus.CLOSED -> SyncCustodialSeries.Status.CLOSED
    },
    isActive = activeBooking,
    openedAt = bookingBeginTime,
    closedAt = bookingEndTime,
    legacyBookingId = bookingId,
    legacyBookingReference = bookingNumber,
  ),
)
