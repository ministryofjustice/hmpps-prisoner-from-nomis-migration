package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits

import org.junit.jupiter.api.extension.ExtendWith
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.OFFICIAL_VISITS_QUEUE_ID
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.OFFICIAL_VISITS_SYNC_QUEUE_ID
import uk.gov.justice.hmpps.sqs.HmppsQueue

@ExtendWith(
  OfficialVisitsDpsApiExtension::class,
)
class OfficialVisitsIntegrationTestBase : SqsIntegrationTestBase() {
  internal val officialVisitsOffenderEventsQueue by lazy { hmppsQueueService.findByQueueId(OFFICIAL_VISITS_SYNC_QUEUE_ID) as HmppsQueue }
  internal val officialVisitsMigrationQueue by lazy { hmppsQueueService.findByQueueId(OFFICIAL_VISITS_QUEUE_ID) as HmppsQueue }

  override fun getQueues() = listOf(officialVisitsOffenderEventsQueue, officialVisitsMigrationQueue)
}
