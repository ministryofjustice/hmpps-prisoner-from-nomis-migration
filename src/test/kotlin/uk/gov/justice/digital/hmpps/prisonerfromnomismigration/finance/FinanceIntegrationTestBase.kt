package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance

import org.junit.jupiter.api.extension.ExtendWith
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.FINANCE_SYNC_QUEUE_ID
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.PRISONER_BALANCE_QUEUE_ID
import uk.gov.justice.hmpps.sqs.HmppsQueue

@ExtendWith(
  FinanceApiExtension::class,
)
abstract class FinanceIntegrationTestBase : SqsIntegrationTestBase() {

  internal val financeOffenderEventsQueue by lazy { hmppsQueueService.findByQueueId(FINANCE_SYNC_QUEUE_ID) as HmppsQueue }
  internal val financeQueueOffenderEventsUrl by lazy { financeOffenderEventsQueue.queueUrl }
  internal val financeQueueOffenderEventsDlqUrl by lazy { financeOffenderEventsQueue.dlqUrl as String }
  internal val awsSqsFinanceOffenderEventsClient by lazy { financeOffenderEventsQueue.sqsClient }
  internal val awsSqsFinanceOffenderEventsDlqClient by lazy { financeOffenderEventsQueue.sqsDlqClient as SqsAsyncClient }

  internal val prisonerBalanceMigrationQueue by lazy { hmppsQueueService.findByQueueId(PRISONER_BALANCE_QUEUE_ID) as HmppsQueue }

  override fun getQueues(): List<HmppsQueue> = listOf(financeOffenderEventsQueue, prisonerBalanceMigrationQueue)
}
