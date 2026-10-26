package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.personlocation

import org.junit.jupiter.api.extension.ExtendWith
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.MovementsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.PERSON_LOCATIONS_SYNC_QUEUE_ID
import uk.gov.justice.hmpps.sqs.HmppsQueue

@ExtendWith(
  PersonLocationDpsApiExtension::class,
)
abstract class PersonLocationIntegrationTestBase : MovementsIntegrationTestBase() {

  internal val personLocationsOffenderEventsQueue by lazy { hmppsQueueService.findByQueueId(PERSON_LOCATIONS_SYNC_QUEUE_ID) as HmppsQueue }

  override fun getQueues(): List<HmppsQueue> = listOf(personLocationsOffenderEventsQueue)
}
