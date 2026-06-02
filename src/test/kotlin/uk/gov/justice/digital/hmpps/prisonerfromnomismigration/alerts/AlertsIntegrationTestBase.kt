package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts

import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.kotlin.times
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.ALERTS_SYNC_QUEUE_ID
import uk.gov.justice.hmpps.sqs.HmppsQueue

@ExtendWith(
  AlertsDpsApiExtension::class,
)
class AlertsIntegrationTestBase : SqsIntegrationTestBase() {

  internal val alertsOffenderEventsQueue by lazy { hmppsQueueService.findByQueueId(ALERTS_SYNC_QUEUE_ID) as HmppsQueue }
  internal val alertsQueueOffenderEventsUrl by lazy { alertsOffenderEventsQueue.queueUrl }
  internal val alertsQueueOffenderEventsDlqUrl by lazy { alertsOffenderEventsQueue.dlqUrl as String }
  internal val awsSqsAlertOffenderEventsClient by lazy { alertsOffenderEventsQueue.sqsClient }
  internal val awsSqsAlertsOffenderEventDlqClient by lazy { alertsOffenderEventsQueue.sqsDlqClient as SqsAsyncClient }

  override fun getQueues(): List<HmppsQueue> = listOf(alertsOffenderEventsQueue)
}
