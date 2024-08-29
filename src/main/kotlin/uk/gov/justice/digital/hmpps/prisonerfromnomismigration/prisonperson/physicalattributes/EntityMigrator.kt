package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.physicalattributes

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.PhysicalAttributesResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.PrisonerPhysicalAttributesResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.PrisonPersonDpsApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.PrisonPersonEntityMigrator
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.PrisonPersonNomisApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.model.PhysicalAttributesMigrationRequest
import java.time.LocalDateTime

@Service("physicalAttributesEntityMigrator")
class EntityMigrator(
  private val prisonPersonNomisApiService: PrisonPersonNomisApiService,
  private val prisonPersonDpsApiService: PrisonPersonDpsApiService,
) : PrisonPersonEntityMigrator<PrisonerPhysicalAttributesResponse, PhysicalAttributesMigrationRequest> {

  override suspend fun getNomisEntity(offenderNo: String) = prisonPersonNomisApiService.getPhysicalAttributes(offenderNo)

  override suspend fun PrisonerPhysicalAttributesResponse.toDpsMigrationRequests() =
    bookings.flatMap { booking ->
      booking.physicalAttributes.map { pa ->
        val (lastModifiedAt, lastModifiedBy) = pa.lastModified()
        prisonPersonDpsApiService.migratePhysicalAttributesRequest(
          heightCentimetres = pa.heightCentimetres,
          weightKilograms = pa.weightKilograms,
          appliesFrom = booking.startDateTime.toLocalDateTime(),
          appliesTo = booking.endDateTime?.toLocalDateTime(),
          createdAt = lastModifiedAt,
          createdBy = lastModifiedBy,
        )
      }
    }

  override suspend fun List<PhysicalAttributesMigrationRequest>.migrate(offenderNo: String) =
    prisonPersonDpsApiService.migratePhysicalAttributes(offenderNo, this).fieldHistoryInserted

  private fun PhysicalAttributesResponse.lastModified(): Pair<LocalDateTime, String> =
    (modifiedDateTime ?: createDateTime).toLocalDateTime() to (modifiedBy ?: createdBy)

  private fun String.toLocalDateTime() = LocalDateTime.parse(this)
}
