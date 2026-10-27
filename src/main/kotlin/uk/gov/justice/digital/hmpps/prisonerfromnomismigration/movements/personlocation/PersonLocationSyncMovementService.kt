package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.personlocation

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.ExternalMovementEvent

@Service
class PersonLocationSyncMovementService {
  suspend fun externalMovementChanged(event: ExternalMovementEvent) {
    // Not yet implemented
  }
}
