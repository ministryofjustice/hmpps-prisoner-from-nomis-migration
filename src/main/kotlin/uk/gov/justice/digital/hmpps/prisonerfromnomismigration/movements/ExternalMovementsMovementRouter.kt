package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.MovementType.TAP
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.taps.TapMovementService

@Service
class ExternalMovementsMovementRouter(
  private val tapMovementService: TapMovementService,
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun externalMovementChanged(event: ExternalMovementEvent) = when (event.movementType) {
    TAP -> tapMovementService.tapMovementChanged(event)
    else -> log.info("Ignoring external movement changed event with type ${event.movementType}, inserted=${event.recordInserted}, deleted=${event.recordDeleted}}")
  }
}
