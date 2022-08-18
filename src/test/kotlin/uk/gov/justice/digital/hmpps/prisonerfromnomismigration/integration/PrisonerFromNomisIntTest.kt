package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration

import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.validIepCreatedMessage

class PrisonerFromNomisIntTest : SqsIntegrationTestBase() {

  @Test
  fun `will consume a prison offender events message`() {

    val message = validIepCreatedMessage()

    awsSqsOffenderEventsClient.sendMessage(queueOffenderEventsUrl, message)

    await untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == 0 }

    // TODO - complete when listener implemented
  }

  private fun getNumberOfMessagesCurrentlyOnQueue(url: String = queueOffenderEventsUrl): Int? {
    val queueAttributes = awsSqsOffenderEventsClient.getQueueAttributes(url, listOf("ApproximateNumberOfMessages"))
    val messagesOnQueue = queueAttributes.attributes["ApproximateNumberOfMessages"]?.toInt()
    return messagesOnQueue
  }
}
