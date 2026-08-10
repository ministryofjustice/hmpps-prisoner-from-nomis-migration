package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.transfer

import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.TRANSFERCHEDULER_SYNC_QUEUE_ID
import uk.gov.justice.hmpps.sqs.HmppsQueue

@ExtendWith(
  TransferScheduleDpsApiExtension::class,
)
abstract class TransferSchedulerIntegrationTestBase : SqsIntegrationTestBase() {

  internal val transferMovementsOffenderEventsQueue by lazy { hmppsQueueService.findByQueueId(TRANSFERCHEDULER_SYNC_QUEUE_ID) as HmppsQueue }
  internal val awsSqsTransferMovementsOffenderEventsClient by lazy { transferMovementsOffenderEventsQueue.sqsClient }
  internal val awsSqsTransferMovementsOffenderEventsDlqClient by lazy { transferMovementsOffenderEventsQueue.sqsDlqClient as SqsAsyncClient }
  internal val transferMovementsQueueOffenderEventsUrl by lazy { transferMovementsOffenderEventsQueue.queueUrl }
  internal val transferMovementsQueueOffenderEventsDlqUrl by lazy { transferMovementsOffenderEventsQueue.dlqUrl as String }

  override fun getQueues(): List<HmppsQueue> = listOf(transferMovementsOffenderEventsQueue)

  @MockitoSpyBean
  protected lateinit var transferScheduleSyncScheduleService: TransferScheduleSyncScheduleService
}
