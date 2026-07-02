package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.property

import org.junit.jupiter.api.extension.ExtendWith
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.PROPERTY_QUEUE_ID
import uk.gov.justice.hmpps.sqs.HmppsQueue

@ExtendWith(PropertyApiExtension::class)
abstract class PropertyIntegrationTestBase : SqsIntegrationTestBase() {

  internal val propertyMigrationQueue by lazy { hmppsQueueService.findByQueueId(PROPERTY_QUEUE_ID) as HmppsQueue }
  internal val awsSqsPropertyMigrationDlqClient by lazy { propertyMigrationQueue.sqsDlqClient }
  internal val propertyMigrationDlqUrl by lazy { propertyMigrationQueue.dlqUrl }

  override fun getQueues(): List<HmppsQueue> = listOf(propertyMigrationQueue)
}
