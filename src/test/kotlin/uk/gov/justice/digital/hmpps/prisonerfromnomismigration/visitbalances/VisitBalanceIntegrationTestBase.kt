package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visitbalances

import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.kotlin.times
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.VISIT_BALANCE_SYNC_QUEUE_ID
import uk.gov.justice.hmpps.sqs.HmppsQueue

@ExtendWith(
  VisitBalanceDpsApiExtension::class,
)
abstract class VisitBalanceIntegrationTestBase : SqsIntegrationTestBase() {

  internal val visitBalanceOffenderEventsQueue by lazy { hmppsQueueService.findByQueueId(VISIT_BALANCE_SYNC_QUEUE_ID) as HmppsQueue }

  override fun getQueues(): List<HmppsQueue> = listOf(visitBalanceOffenderEventsQueue)
}
