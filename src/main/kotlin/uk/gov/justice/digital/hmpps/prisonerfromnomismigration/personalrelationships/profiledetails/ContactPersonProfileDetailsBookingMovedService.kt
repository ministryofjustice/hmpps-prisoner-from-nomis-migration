package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.profiledetails

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.PrisonerBookingMovedDomainEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.ProfileDetailsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService

@Service
class ContactPersonProfileDetailsBookingMovedService(
  private val nomisApiService: NomisApiService,
  private val nomisSyncApiService: ContactPersonProfileDetailsNomisSyncApiService,
  private val syncService: ContactPersonProfileDetailsSyncService,
  private val telemetryClient: TelemetryClient,
) {

  suspend fun bookingMoved(event: PrisonerBookingMovedDomainEvent) {
    val bookingId = event.additionalInformation.bookingId
    val toOffenderNo = event.additionalInformation.movedToNomsNumber
    val fromOffenderNo = event.additionalInformation.movedFromNomsNumber
    val telemetry = mutableMapOf(
      "bookingId" to bookingId.toString(),
      "toOffenderNo" to toOffenderNo,
      "fromOffenderNo" to fromOffenderNo,
    )

    runCatching {
      syncOffenderToDps(fromOffenderNo, telemetry)
      syncOffenderToDps(toOffenderNo, telemetry, mustHaveChanged = true)
      syncOffenderToNomis(toOffenderNo, telemetry)
      telemetryClient.trackEvent("contact-person-booking-moved", telemetry, null)
    }.onFailure { e ->
      raiseErrorTelemetry(e, telemetry)
      throw e
    }
  }

  private fun MutableMap<String, String>.addToTelemetry(telemetryKey: String, addProfileType: String) {
    val profileTypes = this[telemetryKey]
    val newProfileTypes = profileTypes?.split(",")?.plus(addProfileType) ?: listOf(addProfileType)
    this[telemetryKey] = newProfileTypes.joinToString(",")
  }

  suspend fun syncOffenderToDps(
    offenderNo: String,
    telemetry: MutableMap<String, String>,
    mustHaveChanged: Boolean = false,
  ) {
    nomisApiService.getProfileDetails(offenderNo, ContactPersonProfileType.all()).also { nomisResponse ->
      nomisResponse.bookings.firstOrNull { it.latestBooking }
        ?.also { booking ->
          booking.profileDetails.forEach { profileDetail ->
            if (!mustHaveChanged || (profileDetail.lastModifiedDateTime() > booking.startDateTime)) {
              val profileType = ContactPersonProfileType.valueOf(profileDetail.type)
              syncService.profileDetailsChanged(
                offenderNo = offenderNo,
                profileType = ContactPersonProfileType.valueOf(profileDetail.type),
                nomisResponse = nomisResponse,
                forceSync = true,
              )
                .also { telemetry.addToTelemetry("syncToDps", "$offenderNo-$profileType") }
            }
          }
        }
    }
  }

  suspend fun syncOffenderToNomis(offenderNo: String, telemetry: MutableMap<String, String>) {
    ContactPersonProfileType.all().forEach { profileType ->
      nomisSyncApiService.syncProfileDetails(offenderNo, profileType)
        .also { telemetry.addToTelemetry("syncToNomis", "$offenderNo-$profileType") }
    }
  }

  private fun raiseErrorTelemetry(e: Throwable, telemetry: MutableMap<String, String>) {
    telemetry["error"] = e.message.toString()
    telemetryClient.trackEvent("contact-person-booking-moved-error", telemetry.toMap())
  }
}

internal fun ProfileDetailsResponse.lastModifiedDateTime() = modifiedDateTime ?: createDateTime
