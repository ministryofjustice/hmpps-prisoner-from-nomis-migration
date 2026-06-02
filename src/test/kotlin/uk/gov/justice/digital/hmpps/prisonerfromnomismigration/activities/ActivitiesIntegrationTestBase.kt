package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities

import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.kotlin.times
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.ACTIVITIES_QUEUE_ID
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.ALLOCATIONS_QUEUE_ID
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.APPOINTMENTS_QUEUE_ID
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.ActivitiesApiExtension
import uk.gov.justice.hmpps.sqs.HmppsQueue

@ExtendWith(
  ActivitiesApiExtension::class,
)
abstract class ActivitiesIntegrationTestBase : SqsIntegrationTestBase() {

  internal val appointmentsMigrationQueue by lazy { hmppsQueueService.findByQueueId(APPOINTMENTS_QUEUE_ID) as HmppsQueue }
  internal val activitiesMigrationQueue by lazy { hmppsQueueService.findByQueueId(ACTIVITIES_QUEUE_ID) as HmppsQueue }
  internal val allocationsMigrationQueue by lazy { hmppsQueueService.findByQueueId(ALLOCATIONS_QUEUE_ID) as HmppsQueue }

  internal val awsSqsAppointmentsMigrationDlqClient by lazy { appointmentsMigrationQueue.sqsDlqClient }
  internal val awsSqsActivitiesMigrationDlqClient by lazy { activitiesMigrationQueue.sqsDlqClient }
  internal val awsSqsAllocationsMigrationDlqClient by lazy { allocationsMigrationQueue.sqsDlqClient }

  internal val appointmentsMigrationDlqUrl by lazy { appointmentsMigrationQueue.dlqUrl }
  internal val activitiesMigrationDlqUrl by lazy { activitiesMigrationQueue.dlqUrl }
  internal val allocationsMigrationDlqUrl by lazy { allocationsMigrationQueue.dlqUrl }

  override fun getQueues(): List<HmppsQueue> = listOf(activitiesMigrationQueue, appointmentsMigrationQueue, allocationsMigrationQueue)
}
