package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts

import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.boot.test.system.CapturedOutput
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage

private const val BOOKING_ID = 1234L
private const val ALERT_SEQUENCE = 1L

class AlertsSynchronisationIntTest : SqsIntegrationTestBase() {

  @Nested
  @DisplayName("ALERT-INSERTED")
  inner class AlertInserted {
    @Nested
    @DisplayName("Check queue config")
    inner class QueueConfig {
      @BeforeEach
      fun setUp() {
        awsSqsAlertOffenderEventsClient.sendMessage(
          alertsQueueOffenderEventsUrl,
          alertEvent(
            eventType = "ALERT-INSERTED",
          ),
        )
      }

      @Test
      fun `will read the message`(output: CapturedOutput) {
        await untilAsserted {
          await untilAsserted {
            assertThat(output.out).contains("AlertInsertedEvent(bookingId=1234, alertSeq=1)")
          }
        }
      }
    }
  }

  @Nested
  @DisplayName("ALERT-UPDATED")
  inner class AlertUpdated {
    @Nested
    @DisplayName("Check queue config")
    inner class QueueConfig {
      @BeforeEach
      fun setUp() {
        awsSqsAlertOffenderEventsClient.sendMessage(
          alertsQueueOffenderEventsUrl,
          alertEvent(
            eventType = "ALERT-UPDATED",
          ),
        )
      }

      @Test
      fun `will read the message`(output: CapturedOutput) {
        await untilAsserted {
          await untilAsserted {
            assertThat(output.out).contains("AlertUpdatedEvent(bookingId=1234, alertSeq=1)")
          }
        }
      }
    }
  }
}

fun alertEvent(
  eventType: String,
  bookingId: Long = BOOKING_ID,
  alertSeq: Long = ALERT_SEQUENCE,
) = """{
    "MessageId": "ae06c49e-1f41-4b9f-b2f2-dcca610d02cd", "Type": "Notification", "Timestamp": "2019-10-21T14:01:18.500Z", 
    "Message": "{\"eventId\":\"5958295\",\"eventType\":\"$eventType\",\"eventDatetime\":\"2019-10-21T15:00:25.489964\",\"bookingId\": \"$bookingId\",\"alertSeq\": \"$alertSeq\",\"nomisEventType\":\"OFF_ALERT_UPDATE\",\"alertType\":\"L\",\"alertCode\":\"LCE\",\"alertDateTime\":\"2024-02-14T13:24:11\" }",
    "TopicArn": "arn:aws:sns:eu-west-1:000000000000:offender_events", 
    "MessageAttributes": {
      "eventType": {"Type": "String", "Value": "$eventType"}, 
      "id": {"Type": "String", "Value": "8b07cbd9-0820-0a0f-c32f-a9429b618e0b"}, 
      "contentType": {"Type": "String", "Value": "text/plain;charset=UTF-8"}, 
      "timestamp": {"Type": "Number.java.lang.Long", "Value": "1571666478344"}
    }
}
""".trimIndent()
