package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.kotlin.times
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.CORE_PERSON_SYNC_QUEUE_ID
import uk.gov.justice.hmpps.sqs.HmppsQueue

@ExtendWith(
  CorePersonCprApiExtension::class,
)
abstract class CorePersonIntegrationTestBase : SqsIntegrationTestBase() {

  internal val corePersonOffenderEventsQueue by lazy { hmppsQueueService.findByQueueId(CORE_PERSON_SYNC_QUEUE_ID) as HmppsQueue }
  internal val awsSqsCorePersonOffenderEventsClient by lazy { corePersonOffenderEventsQueue.sqsClient }
  internal val corePersonQueueOffenderEventsUrl by lazy { corePersonOffenderEventsQueue.queueUrl }

  override fun getQueues(): List<HmppsQueue> = listOf(corePersonOffenderEventsQueue)
}
