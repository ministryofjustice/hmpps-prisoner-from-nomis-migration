package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csra

import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.kotlin.times
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.CSRA_QUEUE_ID
import uk.gov.justice.hmpps.sqs.HmppsQueue

@ExtendWith(
  CsraApiExtension::class,
)
abstract class CsraIntegrationTestBase : SqsIntegrationTestBase() {

  internal val csraMigrationQueue by lazy { hmppsQueueService.findByQueueId(CSRA_QUEUE_ID) as HmppsQueue }

  override fun getQueues(): List<HmppsQueue> = listOf(csraMigrationQueue)
}
