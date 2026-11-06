package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.bedassignment

import org.junit.jupiter.api.extension.ExtendWith
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.BED_ASSIGNMENT_QUEUE_ID
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.BED_ASSIGNMENT_SYNC_QUEUE_ID
import uk.gov.justice.hmpps.sqs.HmppsQueue

@ExtendWith(BedAssignmentDpsApiExtension::class)
abstract class BedAssignmentIntegrationTestBase : SqsIntegrationTestBase() {
  protected val bedAssignmentMigrationQueue by lazy { hmppsQueueService.findByQueueId(BED_ASSIGNMENT_QUEUE_ID) as HmppsQueue }
  protected val bedAssignmentMigrationDlqUrl by lazy { bedAssignmentMigrationQueue.dlqUrl as String }
  protected val bedAssignmentMigrationDlqClient by lazy { bedAssignmentMigrationQueue.sqsDlqClient!! }

  protected val bedAssignmentEventQueue by lazy { hmppsQueueService.findByQueueId(BED_ASSIGNMENT_SYNC_QUEUE_ID) as HmppsQueue }
  protected val bedAssignmentEventQueueUrl by lazy { bedAssignmentEventQueue.queueUrl }
  protected val bedAssignmentEventDlqUrl by lazy { bedAssignmentEventQueue.dlqUrl as String }
  protected val bedAssignmentEventClient by lazy { bedAssignmentEventQueue.sqsClient }
  protected val bedAssignmentEventDlqClient by lazy { bedAssignmentEventQueue.sqsDlqClient!! }
}
