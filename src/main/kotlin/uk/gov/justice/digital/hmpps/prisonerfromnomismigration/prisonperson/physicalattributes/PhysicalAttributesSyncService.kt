package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.physicalattributes

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.BookingPhysicalAttributesResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.PhysicalAttributesResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.PrisonerPhysicalAttributesResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.PhysicalAttributesChangedEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.synchronisationUser
import java.time.LocalDateTime

@Service
class PhysicalAttributesSyncService(
  private val nomisApiService: PhysicalAttributesNomisApiService,
  private val dpsApiService: PhysicalAttributesDpsApiService,
  private val telemetryClient: TelemetryClient,
) {

  suspend fun physicalAttributesChangedEvent(event: PhysicalAttributesChangedEvent) = physicalAttributesChanged(event.offenderIdDisplay, event.bookingId)

  suspend fun physicalAttributesChanged(
    offenderNo: String,
    bookingId: Long,
    nomisPhysicalAttributes: PrisonerPhysicalAttributesResponse? = null,
    forceSync: Boolean = false,
  ) {
    val telemetry = mutableMapOf(
      "offenderNo" to offenderNo,
      "bookingId" to bookingId.toString(),
    )

    val dpsResponse = try {
      val nomisResponse = nomisPhysicalAttributes ?: nomisApiService.getPhysicalAttributes(offenderNo)

      val booking = nomisResponse.bookings.find { it.bookingId == bookingId }
        ?: throw PhysicalAttributesChangedException("Booking with physical attributes not found for bookingId=$bookingId")
      val physicalAttributes = booking.findLastModifiedPhysicalAttributes()
      telemetry["attributeSequence"] = physicalAttributes.attributeSequence.toString()

      if (!forceSync) {
        getIgnoreReason(nomisResponse, physicalAttributes)?.let { ignoreReason ->
          telemetry["reason"] = ignoreReason
          telemetryClient.trackEvent("physical-attributes-synchronisation-ignored", telemetry)
          return
        }
      }

      dpsApiService.syncPhysicalAttributes(
        offenderNo,
        physicalAttributes.heightCentimetres,
        physicalAttributes.weightKilograms,
        booking.startDateTime.toLocalDateTime(),
        booking.endDateTime?.toLocalDateTime(),
        booking.latestBooking,
        physicalAttributes.lastModifiedDateTime().toLocalDateTime(),
        physicalAttributes.modifiedDateTime?.let { physicalAttributes.modifiedBy } ?: physicalAttributes.createdBy,
      )
    } catch (e: Exception) {
      telemetry["error"] = e.message.toString()
      telemetryClient.trackEvent("physical-attributes-synchronisation-error", telemetry)
      throw e
    }

    telemetry["physicalAttributesHistoryId"] = dpsResponse.fieldHistoryInserted.toString()
    telemetryClient.trackEvent("physical-attributes-synchronisation-updated", telemetry)
  }

  private fun getIgnoreReason(
    nomisResponse: PrisonerPhysicalAttributesResponse,
    physicalAttributes: PhysicalAttributesResponse,
  ): String? = if (nomisResponse.isNewAndEmpty()) {
    "New physical attributes are empty"
  } else if (physicalAttributes.updatedBySync()) {
    "The physical attributes were created by $synchronisationUser"
  } else {
    null
  }

  private fun PrisonerPhysicalAttributesResponse.isNew() = bookings.size == 1 &&
    bookings.first().physicalAttributes.size == 1 &&
    bookings.first().physicalAttributes.first().modifiedDateTime == null

  private fun PrisonerPhysicalAttributesResponse.isNewAndEmpty() = isNew() &&
    bookings.first().physicalAttributes.first().heightCentimetres == null &&
    bookings.first().physicalAttributes.first().weightKilograms == null

  private fun PhysicalAttributesResponse.updatedBySync() = auditModuleName == synchronisationUser
}

class PhysicalAttributesChangedException(message: String) : IllegalArgumentException(message)

internal fun BookingPhysicalAttributesResponse.findLastModifiedPhysicalAttributes() = physicalAttributes
  .maxBy {
    (it.modifiedDateTime ?: it.createDateTime).toLocalDateTime()
  }

internal fun String.toLocalDateTime() = LocalDateTime.parse(this)

internal fun PhysicalAttributesResponse.lastModifiedDateTime() = modifiedDateTime ?: createDateTime
