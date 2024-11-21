package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.PrisonerBookingMovedDomainEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.physicalattributes.PhysicalAttributesNomisApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.physicalattributes.PhysicalAttributesSyncService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.physicalattributes.findLastModifiedPhysicalAttributes
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.physicalattributes.lastModifiedDateTime

@Service
class PrisonPersonMoveBookingService(
  private val telemetryClient: TelemetryClient,
  private val physicalAttributesSyncService: PhysicalAttributesSyncService,
  private val physicalAttributesNomisApiService: PhysicalAttributesNomisApiService,
  private val prisonPersonNomisSyncApiService: PrisonPersonNomisSyncApiService,
) {

  suspend fun bookingMoved(bookingMovedEvent: PrisonerBookingMovedDomainEvent) {
    val bookingId = bookingMovedEvent.additionalInformation.bookingId
    val toOffenderNo = bookingMovedEvent.additionalInformation.movedToNomsNumber
    val fromOffenderNo = bookingMovedEvent.additionalInformation.movedFromNomsNumber
    val telemetry = mutableMapOf(
      "bookingId" to bookingId.toString(),
      "toOffenderNo" to toOffenderNo,
      "fromOffenderNo" to fromOffenderNo,
    )

    try {
      syncFromOffenderDps(fromOffenderNo, telemetry)

      syncToOffenderDps(toOffenderNo, telemetry)

      syncToOffenderNomis(toOffenderNo, telemetry)

      telemetryClient.trackEvent("prisonperson-booking-moved", telemetry.toMap())
    } catch (e: Exception) {
      telemetry += "error" to e.message.toString()
      telemetryClient.trackEvent(
        "prisonperson-booking-moved-error",
        telemetry.toMap(),
      )
      throw e
    }
  }

  private suspend fun syncFromOffenderDps(fromOffenderNo: String, telemetry: MutableMap<String, String>) {
    physicalAttributesNomisApiService.getPhysicalAttributes(fromOffenderNo).also { nomisResponse ->
      val latestBooking = nomisResponse.bookings.find { booking -> booking.latestBooking }
      if (latestBooking != null) {
        physicalAttributesSyncService.physicalAttributesChanged(fromOffenderNo, latestBooking.bookingId, nomisResponse)
        telemetry += "syncFromOffenderDps_HEIGHT" to "true"
        telemetry += "syncFromOffenderDps_WEIGHT" to "true"
      }
    }

    // TODO SDIT-2200 sync all profile details for from offender
  }

  private suspend fun syncToOffenderDps(toOffenderNo: String, telemetry: MutableMap<String, String>) {
    physicalAttributesNomisApiService.getPhysicalAttributes(toOffenderNo).also { nomisResponse ->
      val latestBooking = nomisResponse.bookings.find { booking -> booking.latestBooking }
      if (latestBooking != null &&
        latestBooking.findLastModifiedPhysicalAttributes().lastModifiedDateTime() > latestBooking.startDateTime
      ) {
        physicalAttributesSyncService.physicalAttributesChanged(toOffenderNo, latestBooking.bookingId, nomisResponse)
        telemetry += "syncToOffenderDps_HEIGHT" to "true"
        telemetry += "syncToOffenderDps_WEIGHT" to "true"
      }
    }

    // TODO SDIT-2200 sync profile details that have been updated since the booking started
  }

  private suspend fun syncToOffenderNomis(toOffenderNo: String, telemetry: MutableMap<String, String>) {
    prisonPersonNomisSyncApiService.syncPhysicalAttributes(toOffenderNo)
    telemetry += "syncToOffenderNomis" to "true"
  }
}
