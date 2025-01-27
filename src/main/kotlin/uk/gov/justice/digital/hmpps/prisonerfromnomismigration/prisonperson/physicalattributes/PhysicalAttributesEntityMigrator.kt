package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.physicalattributes

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.PhysicalAttributesResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.PrisonerPhysicalAttributesResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.DpsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.PrisonPersonEntityMigrator
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.model.PhysicalAttributesMigrationRequest
import java.time.LocalDateTime

@Service
class PhysicalAttributesEntityMigrator(
  private val nomisApiService: PhysicalAttributesNomisApiService,
  private val dpsApiService: PhysicalAttributesDpsApiService,
) : PrisonPersonEntityMigrator {

  override suspend fun migrate(offenderNo: String): DpsResponse = getNomisEntity(offenderNo)
    .toDpsMigrationRequests()
    .migrate(offenderNo)
    .let { DpsResponse(it) }

  private suspend fun getNomisEntity(offenderNo: String) = nomisApiService.getPhysicalAttributes(offenderNo)

  private suspend fun PrisonerPhysicalAttributesResponse.toDpsMigrationRequests() = bookings.flatMap { booking ->
    booking.physicalAttributes.map { pa ->
      val (lastModifiedAt, lastModifiedBy) = pa.lastModified()
      dpsApiService.migratePhysicalAttributesRequest(
        heightCentimetres = pa.heightCentimetres,
        weightKilograms = pa.weightKilograms,
        appliesFrom = booking.startDateTime.toLocalDateTime(),
        appliesTo = booking.endDateTime?.toLocalDateTime(),
        createdAt = lastModifiedAt,
        createdBy = lastModifiedBy,
        latestBooking = booking.latestBooking,
      )
    }
  }

  private suspend fun List<PhysicalAttributesMigrationRequest>.migrate(offenderNo: String) = dpsApiService.migratePhysicalAttributes(offenderNo, this).fieldHistoryInserted

  private fun PhysicalAttributesResponse.lastModified(): Pair<LocalDateTime, String> = (modifiedDateTime ?: createDateTime).toLocalDateTime() to (modifiedBy ?: createdBy)

  private fun String.toLocalDateTime() = LocalDateTime.parse(this)
}
