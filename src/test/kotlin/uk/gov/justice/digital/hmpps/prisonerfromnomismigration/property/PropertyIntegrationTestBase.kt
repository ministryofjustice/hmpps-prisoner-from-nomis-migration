package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.property

import org.junit.jupiter.api.extension.ExtendWith
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.PROPERTY_QUEUE_ID
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.PROPERTY_SYNC_QUEUE_ID
import uk.gov.justice.hmpps.sqs.HmppsQueue

@ExtendWith(PropertyApiExtension::class)
abstract class PropertyIntegrationTestBase : SqsIntegrationTestBase() {

  internal val propertyMigrationQueue by lazy { hmppsQueueService.findByQueueId(PROPERTY_QUEUE_ID) as HmppsQueue }
  internal val awsSqsPropertyMigrationDlqClient by lazy { propertyMigrationQueue.sqsDlqClient as SqsAsyncClient }
  internal val propertyMigrationDlqUrl by lazy { propertyMigrationQueue.dlqUrl as String }

  internal val propertyEventQueue by lazy { hmppsQueueService.findByQueueId(PROPERTY_SYNC_QUEUE_ID) as HmppsQueue }
  internal val awsSqsPropertyEventClient by lazy { propertyEventQueue.sqsClient }
  internal val awsSqsPropertyEventDlqClient by lazy { propertyEventQueue.sqsDlqClient as SqsAsyncClient }
  internal val propertyEventQueueUrl by lazy { propertyEventQueue.queueUrl }
  internal val propertyEventDlqUrl by lazy { propertyEventQueue.dlqUrl as String }

  override fun getQueues(): List<HmppsQueue> = listOf(propertyMigrationQueue, propertyEventQueue)
}
