package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.physicalattributes

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PrisonPersonMigrationMappingRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.PhysicalAttributesResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.PrisonerPhysicalAttributesResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.EntityMigrator
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.model.PhysicalAttributesMigrationRequest
import java.time.LocalDateTime

@Service("physicalAttributesEntityMigrator")
class EntityMigrator(
  @Qualifier("physicalAttributesNomisApiService") private val nomisApiService: NomisApiService,
  @Qualifier("physicalAttributesDpsApiService") private val dpsApiService: DpsApiService,
) : EntityMigrator<PrisonerPhysicalAttributesResponse, PhysicalAttributesMigrationRequest> {

  override suspend fun supportsType(klass: Class<out Any>) = klass == PrisonPersonMigrationMappingRequest.MigrationType.PHYSICAL_ATTRIBUTES.javaClass

  override suspend fun getNomisEntity(offenderNo: String) = nomisApiService.getPhysicalAttributes(offenderNo)

  override suspend fun PrisonerPhysicalAttributesResponse.toDpsMigrationRequests() =
    bookings.flatMap { booking ->
      booking.physicalAttributes.map { pa ->
        val (lastModifiedAt, lastModifiedBy) = pa.lastModified()
        dpsApiService.migratePhysicalAttributesRequest(
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
    dpsApiService.migratePhysicalAttributes(offenderNo, this).fieldHistoryInserted

  private fun PhysicalAttributesResponse.lastModified(): Pair<LocalDateTime, String> =
    (modifiedDateTime ?: createDateTime).toLocalDateTime() to (modifiedBy ?: createdBy)

  private fun String.toLocalDateTime() = LocalDateTime.parse(this)
}
