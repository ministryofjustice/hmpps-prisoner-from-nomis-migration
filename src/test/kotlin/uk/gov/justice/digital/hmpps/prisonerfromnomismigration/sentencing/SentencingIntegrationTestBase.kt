package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing

import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.kotlin.times
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.COURT_SENTENCING_SYNC_QUEUE_ID
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SENTENCING_ADJUSTMENTS_SYNC_QUEUE_ID
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.SentencingApiExtension
import uk.gov.justice.hmpps.sqs.HmppsQueue

@ExtendWith(
  SentencingApiExtension::class,
)
abstract class SentencingIntegrationTestBase : SqsIntegrationTestBase() {

  internal val sentencingOffenderEventsQueue by lazy { hmppsQueueService.findByQueueId(SENTENCING_ADJUSTMENTS_SYNC_QUEUE_ID) as HmppsQueue }
  internal val sentencingQueueOffenderEventsUrl by lazy { sentencingOffenderEventsQueue.queueUrl }
  internal val sentencingQueueOffenderEventsDlqUrl by lazy { sentencingOffenderEventsQueue.dlqUrl }
  internal val awsSqsSentencingOffenderEventsClient by lazy { sentencingOffenderEventsQueue.sqsClient }
  internal val awsSqsSentencingOffenderEventsDlqClient by lazy { sentencingOffenderEventsQueue.sqsDlqClient }

  internal val courtSentencingOffenderEventsQueue by lazy { hmppsQueueService.findByQueueId(COURT_SENTENCING_SYNC_QUEUE_ID) as HmppsQueue }

  override fun getQueues(): List<HmppsQueue> = listOf(sentencingOffenderEventsQueue)
}
