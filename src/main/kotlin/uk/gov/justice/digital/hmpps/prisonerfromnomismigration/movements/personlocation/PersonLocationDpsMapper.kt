package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.personlocation

import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.MovementLocation
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.MovementLocation.Type.COURT
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.MovementLocation.Type.HOSPITAL
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.MovementLocation.Type.OTHER
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.MovementLocation.Type.PRISON
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PersonLocationMovement
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PersonLocationMovement.DirectionCode.IN
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PersonLocationMovement.DirectionCode.OUT
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PersonLocationMovement.MovementType.ADM
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PersonLocationMovement.MovementType.CRT
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PersonLocationMovement.MovementType.REL
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PersonLocationMovement.MovementType.TAP
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PersonLocationMovement.MovementType.TRN
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personlocation.model.CodedReason
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personlocation.model.Court
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personlocation.model.Hospital
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personlocation.model.Prison
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personlocation.model.SimpleLocation
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personlocation.model.SyncExternalJourney
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personlocation.model.SyncExternalMovement
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personlocation.model.SyncExternalMovement.Type.ARRIVAL
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personlocation.model.SyncExternalMovement.Type.DEPARTURE
import java.util.UUID

// Shared by the migration and synchronisation services
internal fun PersonLocationMovement.toSyncExternalMovement(
  dpsId: UUID?,
  dpsCustodySeriesId: UUID?,
  scheduleReference: String?,
) = SyncExternalMovement(
  dpsId = dpsId,
  dpsCustodySeriesId = dpsCustodySeriesId,
  type = when (directionCode) {
    IN -> ARRIVAL
    OUT -> DEPARTURE
  },
  reason = CodedReason(
    description = movementReason.description,
    reasonType = "CodedReason",
    code = movementReason.code,
  ),
  occurredAt = movementTime,
  from = toDpsFromLocation(),
  to = to?.toDpsLocation(),
  journey = SyncExternalJourney(
    type = when (movementType) {
      ADM -> SyncExternalJourney.Type.ADMISSION
      CRT -> SyncExternalJourney.Type.COURT_APPEARANCE
      TAP -> SyncExternalJourney.Type.TEMPORARY_ABSENCE
      TRN -> SyncExternalJourney.Type.TRANSFER
      REL -> SyncExternalJourney.Type.RELEASE
    },
    scheduleReference = scheduleReference,
  ),
  legacyBookingId = bookingId,
  legacySequenceNumber = sequence,
  notes = commentText,
)

private fun PersonLocationMovement.toDpsFromLocation() = from?.toDpsLocation()
  ?: when (directionCode) {
    // Some very old inbound NOMIS movements have no from location
    IN -> SimpleLocation(description = "Outside", locationType = "SimpleLocation")
    OUT -> throw IllegalStateException("Outbound movement $bookingId/$sequence has no from location")
  }

private fun MovementLocation.toDpsLocation() = when {
  type == PRISON && code == "UNKNWN" || type == COURT && code == "UNKNCT" -> SimpleLocation(description = "UNKNOWN", locationType = "SimpleLocation")
  else -> when (type) {
    PRISON -> Prison(description = description, locationType = "Prison", code = code!!)
    COURT -> Court(description = description, locationType = "Court", code = code!!)
    HOSPITAL -> Hospital(description = description, locationType = "Hospital", code = code!!)
    OTHER -> SimpleLocation(description = description, locationType = "SimpleLocation")
  }
}
