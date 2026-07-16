package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.court

import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.test.context.bean.override.mockito.MockReset
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.COURTSCHEDULER_SYNC_QUEUE_ID
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationQueueService
import uk.gov.justice.hmpps.sqs.HmppsQueue

@ExtendWith(
  CourtSchedulerDpsApiExtension::class,
)
abstract class CourtSchedulerIntegrationTestBase : SqsIntegrationTestBase() {

  internal val courtMovementsOffenderEventsQueue by lazy { hmppsQueueService.findByQueueId(COURTSCHEDULER_SYNC_QUEUE_ID) as HmppsQueue }
  internal val awsSqsCourtMovementsOffenderEventsClient by lazy { courtMovementsOffenderEventsQueue.sqsClient }
  internal val awsSqsCourtMovementsOffenderEventsDlqClient by lazy { courtMovementsOffenderEventsQueue.sqsDlqClient as SqsAsyncClient }
  internal val courtMovementsQueueOffenderEventsUrl by lazy { courtMovementsOffenderEventsQueue.queueUrl }
  internal val courtMovementsQueueOffenderEventsDlqUrl by lazy { courtMovementsOffenderEventsQueue.dlqUrl as String }

  override fun getQueues(): List<HmppsQueue> = listOf(courtMovementsOffenderEventsQueue)

  @MockitoSpyBean
  protected lateinit var courtSchedulerMigrationService: CourtSchedulerMigrationService

  @MockitoSpyBean(reset = MockReset.NONE)
  protected lateinit var queueService: SynchronisationQueueService

  @MockitoSpyBean(reset = MockReset.NONE)
  protected lateinit var courtSchedulerFeature: CourtSchedulerFeatureSwitches
}
