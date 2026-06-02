package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visits

import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.kotlin.times
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.VISITS_QUEUE_ID
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.VISITS_SYNC_QUEUE_ID
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.VisitsApiExtension
import uk.gov.justice.hmpps.sqs.HmppsQueue

@ExtendWith(
  VisitsApiExtension::class,
)
abstract class VisitsIntegrationTestBase : SqsIntegrationTestBase() {

  internal val visitsMigrationQueue by lazy { hmppsQueueService.findByQueueId(VISITS_QUEUE_ID) as HmppsQueue }
  internal val awsSqsVisitsMigrationDlqClient by lazy { visitsMigrationQueue.sqsDlqClient }

  internal val visitsOffenderEventsQueue by lazy { hmppsQueueService.findByQueueId(VISITS_SYNC_QUEUE_ID) as HmppsQueue }
  internal val visitsQueueOffenderEventsUrl by lazy { visitsOffenderEventsQueue.queueUrl }
  internal val awsSqsVisitsOffenderEventsClient by lazy { visitsOffenderEventsQueue.sqsClient }
  internal val visitsMigrationDlqUrl by lazy { visitsMigrationQueue.dlqUrl }

  override fun getQueues(): List<HmppsQueue> = listOf(visitsMigrationQueue, visitsOffenderEventsQueue)
}
