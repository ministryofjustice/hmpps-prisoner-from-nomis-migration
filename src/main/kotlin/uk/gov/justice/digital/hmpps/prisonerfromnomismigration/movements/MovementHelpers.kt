package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements

import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.EventAudited

enum class DirectionCode { IN, OUT }
enum class MovementType { ADM, CRT, REL, TAP, TRN, }

data class ExternalMovementEvent(
  val bookingId: Long,
  // Some external movements don't have an offenderIdDisplay (REL / TRN) though TAPs always do
  val offenderIdDisplay: String?,
  val movementSeq: Int,
  val movementType: MovementType,
  val directionCode: DirectionCode,
  val recordInserted: Boolean,
  val recordDeleted: Boolean,
  override val auditModuleName: String,
) : EventAudited

internal fun String.toDpsUser() = when (this) {
  "PRISONER_MANAGER_API" -> "SYS"
  else -> this
}
