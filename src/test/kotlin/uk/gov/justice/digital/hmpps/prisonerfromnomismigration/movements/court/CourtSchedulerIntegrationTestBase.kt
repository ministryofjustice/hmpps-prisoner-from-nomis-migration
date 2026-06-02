package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.court

import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.kotlin.times
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.court.CourtSchedulerDpsApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.COURTSCHEDULER_SYNC_QUEUE_ID
import uk.gov.justice.hmpps.sqs.HmppsQueue

@ExtendWith(
  CourtSchedulerDpsApiExtension::class,
)
class CourtSchedulerIntegrationTestBase : SqsIntegrationTestBase() {

  internal val courtMovementsOffenderEventsQueue by lazy { hmppsQueueService.findByQueueId(COURTSCHEDULER_SYNC_QUEUE_ID) as HmppsQueue }
  internal val awsSqsCourtMovementsOffenderEventsClient by lazy { courtMovementsOffenderEventsQueue.sqsClient }
  internal val awsSqsCourtMovementsOffenderEventsDlqClient by lazy { courtMovementsOffenderEventsQueue.sqsDlqClient as SqsAsyncClient }
  internal val courtMovementsQueueOffenderEventsUrl by lazy { courtMovementsOffenderEventsQueue.queueUrl }
  internal val courtMovementsQueueOffenderEventsDlqUrl by lazy { courtMovementsOffenderEventsQueue.dlqUrl as String }

  override fun getQueues(): List<HmppsQueue> = listOf(courtMovementsOffenderEventsQueue)
}
