package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.bedassignment

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.bedassignment.model.NomisBedAssignment
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.locations.LocationsMappingService
import java.util.UUID

@Service
class TranslatorService(
  private val nomisApiService: BedAssignmentNomisApiService,
  private val locationsMappingService: LocationsMappingService,
) {
  suspend fun getHistory(offenderNo: String): List<NomisBedAssignment> {
    val history = nomisApiService.getBedAssignments(offenderNo).bedAssignments
    val locations = history.map { it.livingUnitId }.distinct().associateWith { id ->
      locationsMappingService.getMappingGivenNomisId(id)?.dpsLocationId?.let(UUID::fromString)
    }
    return history.map {
      NomisBedAssignment(
        bookingId = it.bookingId,
        sequence = it.sequence,
        livingUnitId = it.livingUnitId,
        livingUnitDescription = it.livingUnitDescription,
        locationId = locations[it.livingUnitId],
        assignmentDateTime = it.assignmentDateTime,
        assignmentEndDateTime = it.assignmentEndDateTime,
        assignmentReasonCode = it.assignmentReasonCode,
        createdBy = it.audit.createUsername,
      )
    }
  }
}
