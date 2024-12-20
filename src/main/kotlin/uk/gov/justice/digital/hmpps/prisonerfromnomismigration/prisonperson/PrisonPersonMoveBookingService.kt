package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.PrisonerBookingMovedDomainEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.physicalattributes.PhysicalAttributesNomisApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.physicalattributes.PhysicalAttributesSyncService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.physicalattributes.findLastModifiedPhysicalAttributes
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.physicalattributes.lastModifiedDateTime
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.profiledetails.ProfileDetailsNomisApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.profiledetails.ProfileDetailsSyncService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.profiledetails.isPhysicalAttributesProfileType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.profiledetails.lastModifiedDateTime

@Service
class PrisonPersonMoveBookingService(
  private val telemetryClient: TelemetryClient,
  private val physicalAttributesSyncService: PhysicalAttributesSyncService,
  private val physicalAttributesNomisApiService: PhysicalAttributesNomisApiService,
  private val prisonPersonNomisSyncApiService: PrisonPersonNomisSyncApiService,
  private val profileDetailsNomisApiService: ProfileDetailsNomisApiService,
  private val profileDetailsSyncService: ProfileDetailsSyncService,
) {

  /*
   Note that there are some edge cases not catered for here because we've never seen them happen in real life and to
   solve them would complicate this logic even more than it already is. If they do occur we'll pick them up in the
   reconciliation reports and maybe we'll decide to fix.

   The unhandled edge cases are:
   * from and to offender have missing details but some are entered in NOMIS
   * from offender has details, to offender doesn't and NOMIS is not updated
   * to offender has details, from offender doesn't and NOMIS is updated
   More details can be found in the JIRA ticket SDIT-2344 (see attachment).
   */
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
      syncToOffenderDpsIfChanged(toOffenderNo, bookingId, telemetry)
    } catch (e: Exception) {
      raiseErrorTelemetry(e, telemetry)
      throw e
    }

    // Note that we don't rethrow if the sync back to NOMIS fails. The sync back to NOMIS can fail if DPS doesn't
    // have any physical attributes, which doesn't seem to happen in real life but happens a lot in dev where people
    // are testing move booking functionality.
    try {
      syncToOffenderNomis(toOffenderNo, telemetry)
    } catch (e: Exception) {
      raiseErrorTelemetry(e, telemetry)
    }

    telemetryClient.trackEvent("prisonperson-booking-moved", telemetry.toMap())
  }

  private fun raiseErrorTelemetry(e: Exception, telemetry: MutableMap<String, String>) {
    telemetry["error"] = e.message.toString()
    telemetryClient.trackEvent("prisonperson-booking-moved-error", telemetry.toMap())
  }

  private suspend fun syncFromOffenderDps(fromOffenderNo: String, telemetry: MutableMap<String, String>) {
    physicalAttributesNomisApiService.getPhysicalAttributes(fromOffenderNo).also { nomisResponse ->
      nomisResponse.bookings
        .find { it.latestBooking }
        ?.also { latestBooking ->
          physicalAttributesSyncService.physicalAttributesChanged(
            offenderNo = fromOffenderNo,
            bookingId = latestBooking.bookingId,
            nomisPhysicalAttributes = nomisResponse,
            forceSync = true,
          )
          telemetry += "syncFromOffenderDps_HEIGHT" to "true"
          telemetry += "syncFromOffenderDps_WEIGHT" to "true"
        }
    }

    profileDetailsNomisApiService.getProfileDetails(fromOffenderNo).also { nomisResponse ->
      nomisResponse.bookings
        .find { it.latestBooking }
        ?.also { latestBooking ->
          latestBooking.profileDetails
            .filter { it.type.isPhysicalAttributesProfileType() }
            .forEach { profileDetail ->
              profileDetailsSyncService.profileDetailsPhysicalAttributesChanged(
                profileType = profileDetail.type,
                offenderNo = fromOffenderNo,
                bookingId = latestBooking.bookingId,
                nomisProfileDetails = nomisResponse,
                forceSync = true,
              )
              telemetry += "syncFromOffenderDps_${profileDetail.type}" to "true"
            }
        }
    }
  }

  private suspend fun syncToOffenderDpsIfChanged(toOffenderNo: String, bookingId: Long, telemetry: MutableMap<String, String>) {
    physicalAttributesNomisApiService.getPhysicalAttributes(toOffenderNo).also { nomisResponse ->
      nomisResponse.bookings
        .find { it.bookingId == bookingId }
        ?.takeIf { it.latestBooking }
        ?.also { latestBooking ->
          if (latestBooking.findLastModifiedPhysicalAttributes().lastModifiedDateTime() > latestBooking.startDateTime) {
            physicalAttributesSyncService.physicalAttributesChanged(
              offenderNo = toOffenderNo,
              bookingId = latestBooking.bookingId,
              nomisPhysicalAttributes = nomisResponse,
              forceSync = true,
            )
            telemetry += "syncToOffenderDps_HEIGHT" to "true"
            telemetry += "syncToOffenderDps_WEIGHT" to "true"
          }
        }
    }

    profileDetailsNomisApiService.getProfileDetails(toOffenderNo).also { nomisResponse ->
      nomisResponse.bookings
        .find { it.bookingId == bookingId }
        ?.takeIf { it.latestBooking }
        ?.also { latestBooking ->
          latestBooking.profileDetails
            .filter { it.type.isPhysicalAttributesProfileType() }
            .forEach { profileDetail ->
              if (profileDetail.lastModifiedDateTime() > latestBooking.startDateTime) {
                profileDetailsSyncService.profileDetailsPhysicalAttributesChanged(
                  profileType = profileDetail.type,
                  offenderNo = toOffenderNo,
                  bookingId = latestBooking.bookingId,
                  nomisProfileDetails = nomisResponse,
                  forceSync = true,
                )
                telemetry += "syncToOffenderDps_${profileDetail.type}" to "true"
              }
            }
        }
    }
  }

  private suspend fun syncToOffenderNomis(toOffenderNo: String, telemetry: MutableMap<String, String>) {
    prisonPersonNomisSyncApiService.syncPhysicalAttributes(toOffenderNo)
    telemetry += "syncToOffenderNomis" to "true"
  }
}
