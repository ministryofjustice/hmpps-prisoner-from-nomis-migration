package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships

import org.junit.jupiter.api.extension.ExtendWith
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.PERSONALRELATIONSHIPS_DOMAIN_SYNC_QUEUE_ID
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.PERSONALRELATIONSHIPS_SYNC_QUEUE_ID
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.PERSONCONTACTS_DOMAIN_SYNC_QUEUE_ID
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.PRISONERRESTRICTIONS_DOMAIN_SYNC_QUEUE_ID
import uk.gov.justice.hmpps.sqs.HmppsQueue

@ExtendWith(
  ContactPersonDpsApiExtension::class,
)
abstract class PersonalRelationshipsIntegrationTestBase : SqsIntegrationTestBase() {

  internal val personalRelationshipsOffenderEventsQueue by lazy { hmppsQueueService.findByQueueId(PERSONALRELATIONSHIPS_SYNC_QUEUE_ID) as HmppsQueue }
  internal val awsSqsPersonalRelationshipsOffenderEventsClient by lazy { personalRelationshipsOffenderEventsQueue.sqsClient }
  internal val awsSqsPersonalRelationshipsOffenderEventsDlqClient by lazy { personalRelationshipsOffenderEventsQueue.sqsDlqClient as SqsAsyncClient }
  internal val personalRelationshipsQueueOffenderEventsUrl by lazy { personalRelationshipsOffenderEventsQueue.queueUrl }
  internal val personalRelationshipsQueueOffenderEventsDlqUrl by lazy { personalRelationshipsOffenderEventsQueue.dlqUrl as String }

  internal val personalRelationshipsDomainEventsQueue by lazy { hmppsQueueService.findByQueueId(PERSONALRELATIONSHIPS_DOMAIN_SYNC_QUEUE_ID) as HmppsQueue }
  internal val personContactsDomainEventsQueue by lazy { hmppsQueueService.findByQueueId(PERSONCONTACTS_DOMAIN_SYNC_QUEUE_ID) as HmppsQueue }
  internal val prisonerRestrictionsDomainEventsQueue by lazy { hmppsQueueService.findByQueueId(PRISONERRESTRICTIONS_DOMAIN_SYNC_QUEUE_ID) as HmppsQueue }

  override fun getQueues(): List<HmppsQueue> = listOf(
    personalRelationshipsOffenderEventsQueue,
    personalRelationshipsDomainEventsQueue,
    personContactsDomainEventsQueue,
    prisonerRestrictionsDomainEventsQueue,
  )
}
