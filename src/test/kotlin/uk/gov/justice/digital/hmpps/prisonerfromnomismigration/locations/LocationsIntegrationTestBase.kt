package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.locations

import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.kotlin.times
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.LOCATIONS_SYNC_QUEUE_ID
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.LocationsApiExtension
import uk.gov.justice.hmpps.sqs.HmppsQueue

@ExtendWith(
  LocationsApiExtension::class,
)
abstract class LocationsIntegrationTestBase : SqsIntegrationTestBase() {

  internal val locationsOffenderEventsQueue by lazy { hmppsQueueService.findByQueueId(LOCATIONS_SYNC_QUEUE_ID) as HmppsQueue }
  internal val locationsQueueOffenderEventsUrl by lazy { locationsOffenderEventsQueue.queueUrl }
  internal val locationsQueueOffenderEventsDlqUrl by lazy { locationsOffenderEventsQueue.dlqUrl as String }
  internal val awsSqsLocationsOffenderEventsClient by lazy { locationsOffenderEventsQueue.sqsClient }
  internal val awsSqsLocationsOffenderEventDlqClient by lazy { locationsOffenderEventsQueue.sqsDlqClient as SqsAsyncClient }

  override fun getQueues(): List<HmppsQueue> = listOf(locationsOffenderEventsQueue)
}
