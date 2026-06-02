package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.taps

import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.kotlin.times
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.taps.TapDpsApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.taps.TapMigrationService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.EXTERNALMOVEMENTS_SYNC_QUEUE_ID
import uk.gov.justice.hmpps.sqs.HmppsQueue

@ExtendWith(
  TapDpsApiExtension::class,
)
abstract class TapIntegrationTestBase : SqsIntegrationTestBase() {

  internal val externalMovementsOffenderEventsQueue by lazy { hmppsQueueService.findByQueueId(EXTERNALMOVEMENTS_SYNC_QUEUE_ID) as HmppsQueue }
  internal val awsSqsExternalMovementsOffenderEventsClient by lazy { externalMovementsOffenderEventsQueue.sqsClient }
  internal val awsSqsExternalMovementsOffenderEventsDlqClient by lazy { externalMovementsOffenderEventsQueue.sqsDlqClient as SqsAsyncClient }
  internal val externalMovementsQueueOffenderEventsUrl by lazy { externalMovementsOffenderEventsQueue.queueUrl }
  internal val externalMovementsQueueOffenderEventsDlqUrl by lazy { externalMovementsOffenderEventsQueue.dlqUrl as String }

  override fun getQueues(): List<HmppsQueue> = listOf(externalMovementsOffenderEventsQueue)

  @MockitoSpyBean
  protected lateinit var externalMovementsMigrationService: TapMigrationService
}
