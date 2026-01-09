package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonDisabilityStatus
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonImmigrationStatus
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonNationality
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonSexualOrientation
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.TelemetryEnabled
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.telemetryOf
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.track
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.BookingProfileDetailsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.ProfileDetailsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService

private const val TELEMETRY_PREFIX = "coreperson-profiledetails-synchronisation"

@Service
class CorePersonSynchronisationProfileDetailsService(
  override val telemetryClient: TelemetryClient,
  private val nomisApiService: NomisApiService,
  private val corePersonCprApiService: CorePersonCprApiService,
) : TelemetryEnabled {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
    val eventProfileTypes = listOf("NAT", "NATIO", "SEXO", "DISABILITY", "IMM")
  }
  suspend fun offenderProfileDetailsChanged(event: OffenderProfileDetailsEvent) {
    val (offenderIdDisplay, bookingId, profileType) = event
    val telemetry = telemetryOf(
      "offenderNo" to offenderIdDisplay,
      "bookingId" to bookingId.toString(),
      "profileType" to profileType,
    )
    if (eventProfileTypes.contains(profileType)) {
      val profileTypes = if (profileType == "NAT" || profileType == "NATIO") {
        listOf("NAT", "NATIO") // NATIO is multiple languages free text
      } else {
        listOf(profileType)
      }

      track(TELEMETRY_PREFIX, telemetry) {
        val nomisResponse = nomisApiService.getProfileDetails(event.offenderIdDisplay, profileTypes)

        val typeHistory = nomisResponse.bookings
          .mapNotNull { booking ->
            booking.profileDetails
              .find { profile -> profile.type == profileType }
              ?.let { booking to it }
          }
          .sortedBy { it.first.sequence }

        if (!typeHistory[0].first.latestBooking) {
          throw BookingException("Could not find latest booking")
        }
        if (typeHistory[0].first.bookingId != bookingId) {
          telemetryClient.trackEvent("$TELEMETRY_PREFIX-ignored-booking", telemetry)
          return
        }
        if (firstIsADuplicate(typeHistory)) {
          telemetryClient.trackEvent("$TELEMETRY_PREFIX-ignored-duplicate", telemetry)
          return
        }

        // NOTE the profile create/modify dates are COPIED from one booking to another, so do not refer to the row insert or update time
        // (but the auditTimestamp does)

        with(typeHistory[0].second) {
          when (profileType) {
            "SEXO" -> {
              corePersonCprApiService.syncCreateSexualOrientation(
                offenderIdDisplay,
                PrisonSexualOrientation(
                  sexualOrientationCode = code,
                  modifyUserId = modifiedBy ?: createdBy,
                  modifyDateTime = modifiedDateTime ?: createDateTime,
                ),
              )
            }

            "DISABILITY" -> {
              corePersonCprApiService.syncCreateDisability(
                offenderIdDisplay,
                PrisonDisabilityStatus(
                  disability = code == "YES",
                  modifyUserId = modifiedBy ?: createdBy,
                  modifyDateTime = modifiedDateTime ?: createDateTime,
                ),
              )
            }

            "IMM" -> {
              corePersonCprApiService.syncCreateImmigrationStatus(
                PrisonImmigrationStatus(
                  prisonNumber = offenderIdDisplay,
                  interestToImmigration = code == "Y",
                  current = true,
                  createUserId = modifiedBy ?: createdBy,
                  createDateTime = modifiedDateTime ?: createDateTime,
                ),
              )
            }

            "NAT", "NATIO" -> {
              val latestBooking = nomisResponse.bookings.find { it.latestBooking }!!
              val nat = latestBooking.profileDetails.find { it.type == "NAT" }
              val natio = latestBooking.profileDetails.find { it.type == "NATIO" }
              corePersonCprApiService.syncCreateNationality(
                offenderIdDisplay,
                PrisonNationality(
                  nationalityCode = nat?.code,
                  modifyUserId = modifiedBy ?: createdBy,
                  modifyDateTime = modifiedDateTime ?: createDateTime,
                  notes = natio?.code,
                ),
              )
            }
          }
          code ?: log.warn("offenderProfileDetailsChanged(): Null value for offender $offenderIdDisplay, booking $bookingId, profile $profileType")
          // Not sure how rare this is, so log for now
        }
      }
    } else {
      telemetryClient.trackEvent("$TELEMETRY_PREFIX-ignored-type", telemetry)
    }
  }
}

private fun firstIsADuplicate(typeHistory: List<Pair<BookingProfileDetailsResponse, ProfileDetailsResponse>>): Boolean = typeHistory.size >= 2 && typeHistory[0].second.code == typeHistory[1].second.code
