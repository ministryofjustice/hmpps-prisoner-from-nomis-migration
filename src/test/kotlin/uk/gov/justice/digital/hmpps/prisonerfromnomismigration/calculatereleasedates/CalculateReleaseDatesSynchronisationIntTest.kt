package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.calculatereleasedates

import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.Nested
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.countAllMessagesOnDLQQueue
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage
import uk.gov.justice.hmpps.sqs.HmppsQueue
import uk.gov.justice.hmpps.sqs.countAllMessagesOnQueue

class CalculateReleaseDatesSynchronisationIntTest : CalculateReleaseDatesIntegrationTestBase() {
  override fun getQueues(): List<HmppsQueue> = listOf(calculateReleaseDatesEventQueue)

  @Nested
  inner class SupportedEvents {
    @ParameterizedTest
    @ValueSource(
      strings = [
        "OFFENDER_SENT_CALCULATIONS-INSERTED",
        "OFFENDER_SENT_CALCULATIONS-UPDATED",
        "OFFENDER_SENT_CALCULATIONS-DELETED",
      ],
    )
    fun `will consume the event without errors`(eventType: String) {
      calculateReleaseDatesEventQueue.sendMessage(calculateReleaseDatesEventMessage(eventType))

      await untilCallTo {
        calculateReleaseDatesEventQueue.sqsClient.countAllMessagesOnQueue(calculateReleaseDatesEventQueue.queueUrl).get()
      } matches { it == 0 }
      assertThat(calculateReleaseDatesEventQueue.countAllMessagesOnDLQQueue()).isZero()
    }
  }

  @Nested
  inner class UnsupportedEvents {
    @ParameterizedTest
    @ValueSource(strings = ["OFFENDER_SENT_CALCULATIONS-OTHER", "SOME_OTHER_EVENT-INSERTED"])
    fun `will consume and ignore unexpected events`(eventType: String) {
      calculateReleaseDatesEventQueue.sendMessage(calculateReleaseDatesEventMessage(eventType))

      await untilCallTo {
        calculateReleaseDatesEventQueue.sqsClient.countAllMessagesOnQueue(calculateReleaseDatesEventQueue.queueUrl).get()
      } matches { it == 0 }
      assertThat(calculateReleaseDatesEventQueue.countAllMessagesOnDLQQueue()).isZero()
    }
  }
}
