package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing

import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.kotlin.times
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.COURT_SENTENCING_QUEUE_ID
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.COURT_SENTENCING_SYNC_QUEUE_ID
import uk.gov.justice.hmpps.sqs.HmppsQueue

@ExtendWith(
  CourtSentencingDpsApiExtension::class,
)
abstract class CourtSentencingIntegrationTestBase : SqsIntegrationTestBase() {

  internal val courtSentencingMigrationQueue by lazy { hmppsQueueService.findByQueueId(COURT_SENTENCING_QUEUE_ID) as HmppsQueue }

  internal val courtSentencingOffenderEventsQueue by lazy { hmppsQueueService.findByQueueId(COURT_SENTENCING_SYNC_QUEUE_ID) as HmppsQueue }
  internal val courtSentencingQueueOffenderEventsUrl by lazy { courtSentencingOffenderEventsQueue.queueUrl }
  internal val courtSentencingQueueOffenderEventsDlqUrl by lazy { courtSentencingOffenderEventsQueue.dlqUrl as String }
  internal val awsSqsCourtSentencingOffenderEventsClient by lazy { courtSentencingOffenderEventsQueue.sqsClient }
  internal val awsSqsCourtSentencingOffenderEventDlqClient by lazy { courtSentencingOffenderEventsQueue.sqsDlqClient as SqsAsyncClient }
  internal val courtSentencingMigrationDlqUrl by lazy { courtSentencingMigrationQueue.dlqUrl as String }
  internal val awsSqsCourtSentencingMigrationDlqClient by lazy { courtSentencingMigrationQueue.sqsDlqClient }

  override fun getQueues(): List<HmppsQueue> = listOf(courtSentencingMigrationQueue, courtSentencingOffenderEventsQueue)
}
