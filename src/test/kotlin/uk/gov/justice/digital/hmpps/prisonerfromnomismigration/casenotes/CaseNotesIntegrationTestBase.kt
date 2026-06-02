package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes

import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.kotlin.times
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.CASENOTES_SYNC_QUEUE_ID
import uk.gov.justice.hmpps.sqs.HmppsQueue

@ExtendWith(
  CaseNotesApiExtension::class,
)
abstract class CaseNotesIntegrationTestBase : SqsIntegrationTestBase() {

  internal val caseNotesOffenderEventsQueue by lazy { hmppsQueueService.findByQueueId(CASENOTES_SYNC_QUEUE_ID) as HmppsQueue }
  internal val caseNotesQueueOffenderEventsUrl by lazy { caseNotesOffenderEventsQueue.queueUrl }
  internal val caseNotesQueueOffenderEventsDlqUrl by lazy { caseNotesOffenderEventsQueue.dlqUrl as String }
  internal val awsSqsCaseNoteOffenderEventsClient by lazy { caseNotesOffenderEventsQueue.sqsClient }
  internal val awsSqsCaseNotesOffenderEventsDlqClient by lazy { caseNotesOffenderEventsQueue.sqsDlqClient as SqsAsyncClient }

  override fun getQueues(): List<HmppsQueue> = listOf(caseNotesOffenderEventsQueue)
}
