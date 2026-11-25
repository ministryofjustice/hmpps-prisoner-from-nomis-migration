package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.calculatereleasedates

import org.junit.jupiter.api.extension.ExtendWith
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.CALCULATE_RELEASE_DATES_SYNC_QUEUE_ID
import uk.gov.justice.hmpps.sqs.HmppsQueue

@ExtendWith(CalculateReleaseDatesDpsApiExtension::class)
abstract class CalculateReleaseDatesIntegrationTestBase : SqsIntegrationTestBase() {
  protected val calculateReleaseDatesEventQueue by lazy { hmppsQueueService.findByQueueId(CALCULATE_RELEASE_DATES_SYNC_QUEUE_ID) as HmppsQueue }
}
