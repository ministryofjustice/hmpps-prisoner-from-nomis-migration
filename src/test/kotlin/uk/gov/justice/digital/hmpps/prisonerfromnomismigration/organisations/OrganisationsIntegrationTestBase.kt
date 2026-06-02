package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations

import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.kotlin.times
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.ORGANISATIONS_SYNC_QUEUE_ID
import uk.gov.justice.hmpps.sqs.HmppsQueue

@ExtendWith(
  OrganisationsDpsApiExtension::class,
)
abstract class OrganisationsIntegrationTestBase : SqsIntegrationTestBase() {

  internal val organisationsOffenderEventsQueue by lazy { hmppsQueueService.findByQueueId(ORGANISATIONS_SYNC_QUEUE_ID) as HmppsQueue }

  override fun getQueues(): List<HmppsQueue> = listOf(organisationsOffenderEventsQueue)
}
