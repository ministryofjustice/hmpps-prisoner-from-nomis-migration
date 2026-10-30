package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.personlocation

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.EventAudited
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.EventAudited.Companion.DPS_SYNC_AUDIT_MODULE
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.SuccessOrDuplicate
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.TelemetryEnabled
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.track
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.valuesAsStrings
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.personlocation.PersonLocationRetryMappingMessageTypes.RETRY_MAPPING_PERSON_LOCATION_BOOKING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.toDpsUser
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonLocationBookingIdMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonLocationBookingIdMappingDto.MappingType.NOMIS_CREATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PersonLocationBooking
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personlocation.model.SyncCustodialSeries
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personlocation.model.SyncCustodialSeriesRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personlocation.model.SyncUser
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.InternalMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationType
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
  private val queueService: SynchronisationQueueService,
) : TelemetryEnabled {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun bookingUpdated(event: BookingStatusEvent) {
    val (bookingId, prisonerNumber) = event
    val telemetry = event.telemetry()

    if (event.auditExactMatchOrHasMissingAudit(DPS_SYNC_AUDIT_MODULE)) {
      telemetryClient.trackEvent("$TELEMETRY_PREFIX-updated-ignored", telemetry)
      return
    }

    track("$TELEMETRY_PREFIX-updated", telemetry) {
      // The update may have overtaken the create, so fail and retry if there is no mapping yet
      val mapping = mappingApiService.getBookingMappingOrNull(bookingId)
        ?.also { telemetry["dpsCustodialSeriesId"] = it.dpsCustodialSeriesId }
        ?: throw IllegalStateException("No person location booking mapping found for booking $bookingId")

      val nomis = nomisApiService.getPersonLocationBooking(prisonerNumber, bookingId)
      dpsApiService.syncCustodialSeries(prisonerNumber, nomis.toDpsRequest(dpsId = mapping.dpsCustodialSeriesId))
    }
  }

  suspend fun bookingDeleted(event: BookingStatusEvent) {
    val bookingId = event.bookingId
    val telemetry = event.telemetry()

    if (event.auditExactMatchOrHasMissingAudit(DPS_SYNC_AUDIT_MODULE)) {
      telemetryClient.trackEvent("$TELEMETRY_PREFIX-deleted-ignored", telemetry)
      return
    }

    mappingApiService.getBookingMappingOrNull(bookingId)
      ?.also { telemetry["dpsCustodialSeriesId"] = it.dpsCustodialSeriesId }
      ?.also { mapping ->
        track("$TELEMETRY_PREFIX-deleted", telemetry) {
          dpsApiService.deleteCustodialSeries(mapping.dpsCustodialSeriesId)
          mappingApiService.deleteBookingMapping(bookingId)
        }
      }
      ?: telemetryClient.trackEvent("$TELEMETRY_PREFIX-deleted-ignored", telemetry)
  }

  suspend fun bookingInserted(event: BookingStatusEvent) {
    val (bookingId, prisonerNumber) = event
    val telemetry = event.telemetry()

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

      PersonLocationBookingIdMappingDto(
        nomisBookingId = bookingId,
        dpsCustodialSeriesId = dps.dpsId,
        mappingType = NOMIS_CREATED,
      ).also { tryToCreateMapping(it, telemetry) }
    }
  }

  private suspend fun tryToCreateMapping(mapping: PersonLocationBookingIdMappingDto, telemetry: Map<String, Any>) {
    try {
      mappingApiService.createBookingMapping(mapping).takeIf { it.isError }?.also { trackDuplicate(it) }
    } catch (e: Exception) {
      log.error("Failed to create mapping for person location booking ${mapping.nomisBookingId}", e)
      queueService.sendMessage(
        messageType = RETRY_MAPPING_PERSON_LOCATION_BOOKING.name,
        synchronisationType = SynchronisationType.PERSON_LOCATIONS,
        message = mapping,
        telemetryAttributes = telemetry.valuesAsStrings(),
      )
    }
  }

  suspend fun retryCreateBookingMapping(retryMessage: InternalMessage<PersonLocationBookingIdMappingDto>) {
    mappingApiService.createBookingMapping(retryMessage.body).also {
      if (it.isError) {
        trackDuplicate(it)
      } else {
        telemetryClient.trackEvent("$TELEMETRY_PREFIX-mapping-retry-created", retryMessage.telemetryAttributes)
      }
    }
  }

  private fun trackDuplicate(result: SuccessOrDuplicate<PersonLocationBookingIdMappingDto>) {
    with(result.errorResponse!!.moreInfo) {
      telemetryClient.trackEvent(
        "$TELEMETRY_PREFIX-inserted-duplicate",
        mapOf(
          "existingBookingId" to existing!!.nomisBookingId,
          "existingDpsCustodialSeriesId" to existing.dpsCustodialSeriesId,
          "duplicateBookingId" to duplicate.nomisBookingId,
          "duplicateDpsCustodialSeriesId" to duplicate.dpsCustodialSeriesId,
        ),
      )
    }
  }
}

private fun BookingStatusEvent.telemetry() = mutableMapOf<String, Any>(
  "offenderNo" to offenderIdDisplay,
  "bookingId" to bookingId,
  "bookingSeq" to bookingSeq,
).apply { oldBookingSeq?.also { put("oldBookingSeq", it) } }

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
