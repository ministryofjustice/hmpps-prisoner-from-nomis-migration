package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csra

import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.kotlin.times
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.CSRA_QUEUE_ID
import uk.gov.justice.hmpps.sqs.HmppsQueue

@ExtendWith(
  CsraApiExtension::class,
)
abstract class CsraIntegrationTestBase : SqsIntegrationTestBase() {

  protected val csraMigrationQueue by lazy { hmppsQueueService.findByQueueId(CSRA_QUEUE_ID) as HmppsQueue }

  protected val csraQueueMigrationDlqUrl by lazy { csraMigrationQueue.dlqUrl as String }
  protected val awsSqsCsraMigrationDlqClient by lazy { csraMigrationQueue.sqsDlqClient as SqsAsyncClient }

  protected val csraEventQueue by lazy { hmppsQueueService.findByQueueId("eventcsra") as HmppsQueue }
  protected val awsSqsCsraEventClient by lazy { csraEventQueue.sqsClient }
  protected val awsSqsCsraEventDlqClient by lazy { csraEventQueue.sqsDlqClient!! }
  protected val csraEventQueueUrl by lazy { csraEventQueue.queueUrl }
  protected val csraEventDlqUrl by lazy { csraEventQueue.dlqUrl as String }
}
