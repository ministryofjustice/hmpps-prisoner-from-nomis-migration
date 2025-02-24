package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.profiledetails

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.PrisonerProfileDetailsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.ProfileDetailsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.DpsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.PrisonPersonEntityMigrator
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.atPrisonPersonZone
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.model.MigrationValueWithMetadataString
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.model.ProfileDetailsPhysicalAttributesMigrationRequest
import java.time.LocalDateTime

@Service
class ProfileDetailPhysicalAttributesEntityMigrator(
  private val nomisApiService: ProfileDetailsNomisApiService,
  private val dpsApiService: ProfileDetailPhysicalAttributesDpsApiService,
) : PrisonPersonEntityMigrator {

  override suspend fun migrate(offenderNo: String): DpsResponse = getNomisEntity(offenderNo)
    .toDpsMigrationRequests()
    .migrate(offenderNo)
    .let { DpsResponse(it) }

  private suspend fun getNomisEntity(offenderNo: String) = nomisApiService.getProfileDetails(offenderNo)

  private suspend fun PrisonerProfileDetailsResponse.toDpsMigrationRequests() = bookings.map { booking ->
    with(booking) {
      ProfileDetailsPhysicalAttributesMigrationRequest(
        appliesFrom = startDateTime.toLocalDateTime().atPrisonPersonZone(),
        // We no longer return booking end date from the API call and this service isn't used anyway
        appliesTo = "",
        build = profileDetails.toDpsRequest("BUILD"),
        shoeSize = profileDetails.toDpsRequest("SHOESIZE"),
        hair = profileDetails.toDpsRequest("HAIR"),
        facialHair = profileDetails.toDpsRequest("FACIAL_HAIR"),
        face = profileDetails.toDpsRequest("FACE"),
        leftEyeColour = profileDetails.toDpsRequest("L_EYE_C"),
        rightEyeColour = profileDetails.toDpsRequest("R_EYE_C"),
        latestBooking = booking.latestBooking,
      )
    }
  }

  private fun List<ProfileDetailsResponse>.toDpsRequest(nomisType: String) = find { it.type == nomisType }?.let {
    val (lastModifiedAt, lastModifiedBy) = it.lastModified()
    MigrationValueWithMetadataString(
      value = it.code,
      lastModifiedAt = lastModifiedAt.atPrisonPersonZone(),
      lastModifiedBy = lastModifiedBy,
    )
  }

  private fun ProfileDetailsResponse.lastModified(): Pair<LocalDateTime, String> = (modifiedDateTime ?: createDateTime).toLocalDateTime() to (modifiedBy ?: createdBy)

  private suspend fun List<ProfileDetailsPhysicalAttributesMigrationRequest>.migrate(offenderNo: String) = dpsApiService.migrateProfileDetailsPhysicalAttributes(offenderNo, this).fieldHistoryInserted

  private fun String.toLocalDateTime() = LocalDateTime.parse(this)
}
