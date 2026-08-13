package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.agency

import org.junit.jupiter.api.extension.ExtendWith
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.AGENCY_REGISTERS_QUEUE_ID
import uk.gov.justice.hmpps.sqs.HmppsQueue

@ExtendWith(
  AgencyRegistersDpsApiExtension::class,
)
abstract class AgencyRegistersIntegrationTestBase : SqsIntegrationTestBase() {
  internal val agencyRegistersMigrationQueue by lazy { hmppsQueueService.findByQueueId(AGENCY_REGISTERS_QUEUE_ID) as HmppsQueue }

  override fun getQueues() = listOf(agencyRegistersMigrationQueue)
}
