package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.staff

import org.junit.jupiter.api.extension.ExtendWith
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.STAFF_QUEUE_ID
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.STAFF_SYNC_QUEUE_ID
import uk.gov.justice.hmpps.sqs.HmppsQueue

@ExtendWith(
  StaffDpsApiExtension::class,
)
abstract class StaffIntegrationTestBase : SqsIntegrationTestBase() {

  internal val staffMigrationQueue by lazy { hmppsQueueService.findByQueueId(STAFF_QUEUE_ID) as HmppsQueue }
  internal val staffOffenderEventsQueue by lazy { hmppsQueueService.findByQueueId(STAFF_SYNC_QUEUE_ID) as HmppsQueue }

  override fun getQueues(): List<HmppsQueue> = listOf(staffMigrationQueue, staffOffenderEventsQueue)
}
