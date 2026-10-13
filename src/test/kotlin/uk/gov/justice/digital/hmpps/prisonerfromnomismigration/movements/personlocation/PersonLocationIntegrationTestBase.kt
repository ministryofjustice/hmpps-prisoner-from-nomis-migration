package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.personlocation

import org.junit.jupiter.api.extension.ExtendWith
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.MovementsIntegrationTestBase

@ExtendWith(
  PersonLocationDpsApiExtension::class,
)
abstract class PersonLocationIntegrationTestBase : MovementsIntegrationTestBase()
