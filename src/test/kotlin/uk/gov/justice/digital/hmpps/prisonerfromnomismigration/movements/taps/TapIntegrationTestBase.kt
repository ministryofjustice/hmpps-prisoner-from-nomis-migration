package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.taps

import org.junit.jupiter.api.extension.ExtendWith
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.MovementsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.EXTERNALMOVEMENTS_SYNC_QUEUE_ID
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.EXTERNAL_MOVEMENTS_QUEUE_ID
import uk.gov.justice.hmpps.sqs.HmppsQueue

@ExtendWith(
  TapDpsApiExtension::class,
)
abstract class TapIntegrationTestBase : MovementsIntegrationTestBase() {

  internal val externalMovementsOffenderEventsQueue by lazy { hmppsQueueService.findByQueueId(EXTERNALMOVEMENTS_SYNC_QUEUE_ID) as HmppsQueue }
  internal val awsSqsExternalMovementsOffenderEventsClient by lazy { externalMovementsOffenderEventsQueue.sqsClient }
  internal val awsSqsExternalMovementsOffenderEventsDlqClient by lazy { externalMovementsOffenderEventsQueue.sqsDlqClient as SqsAsyncClient }
  internal val externalMovementsQueueOffenderEventsUrl by lazy { externalMovementsOffenderEventsQueue.queueUrl }
  internal val externalMovementsQueueOffenderEventsDlqUrl by lazy { externalMovementsOffenderEventsQueue.dlqUrl as String }
  internal val externalMovementsMappingRetryQueue by lazy { hmppsQueueService.findByQueueId(EXTERNAL_MOVEMENTS_QUEUE_ID) as HmppsQueue }

  override fun getQueues(): List<HmppsQueue> = listOf(externalMovementsOffenderEventsQueue, externalMovementsMappingRetryQueue)
}
