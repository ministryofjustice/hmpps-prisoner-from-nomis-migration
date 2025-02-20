package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.profiledetails

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.ProfileDetailsResponse
import java.time.LocalDateTime

@Service
class ContactPersonProfileDetailsSyncService(
  private val nomisApi: ContactPersonProfileDetailsNomisApiService,
  private val dpsApi: ContactPersonProfileDetailsDpsApiService,
  private val telemetryClient: TelemetryClient,
) {

  suspend fun profileDetailsChanged(profileType: String, offenderNo: String, bookingId: Long) {
    when (profileType) {
      "MARITAL" -> domesticStatusChanged(offenderNo, bookingId)
    }
  }

  private suspend fun domesticStatusChanged(offenderNo: String, bookingId: Long) {
    val telemetry = mutableMapOf(
      "offenderNo" to offenderNo,
      "bookingId" to bookingId.toString(),
    )

    val dpsResponse = try {
      val nomisResponse = nomisApi.getProfileDetails(offenderNo)
      val booking = nomisResponse.bookings.find { it.bookingId == bookingId }
        ?: throw DomesticStatusChangedException("No booking found for bookingId $bookingId")
      telemetry["latestBooking"] = booking.latestBooking.toString()
      val profileDetails = booking.profileDetails.find { it.type == "MARITAL" }
        ?: throw DomesticStatusChangedException("No MARITAL profile found for bookingId $bookingId")
      val (lastModifiedTime, lastModifiedBy) = profileDetails.lastModified()

      dpsApi.syncDomesticStatus(
        offenderNo,
        DomesticStatusSyncRequest(
          profileDetails.code,
          lastModifiedBy,
          lastModifiedTime,
          booking.latestBooking,
        ),
      )
    } catch (e: Exception) {
      telemetry["error"] = e.message.toString()
      telemetryClient.trackEvent("contact-person-domestic-status-synchronisation-error", telemetry)
      throw e
    }

    telemetry["dpsId"] = dpsResponse.domesticStatusId.toString()
    telemetryClient.trackEvent("contact-person-domestic-status-synchronisation-success", telemetry)
  }

  private fun ProfileDetailsResponse.lastModified(): Pair<LocalDateTime, String> = (modifiedDateTime ?: createDateTime).toLocalDateTime() to (modifiedBy ?: createdBy)

  private fun String.toLocalDateTime() = LocalDateTime.parse(this)
}

class DomesticStatusChangedException(message: String) : IllegalArgumentException(message)
