package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.calculatereleasedates

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.core.read.ListAppender
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.json.JsonTest
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.EventFeatureSwitch

@JsonTest
internal class CalculateReleaseDatesSynchronisationEventListenerTest(@Autowired private val jsonMapper: JsonMapper) {
  private val eventFeatureSwitch: EventFeatureSwitch = mock()
  private val listener = CalculateReleaseDatesSynchronisationEventListener(jsonMapper, eventFeatureSwitch)
  private val appender = ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>()
  private val logger = LoggerFactory.getLogger(CalculateReleaseDatesSynchronisationEventListener::class.java) as Logger

  @BeforeEach
  fun setUp() {
    logger.level = Level.INFO
    appender.start()
    logger.addAppender(appender)
  }

  @AfterEach
  fun tearDown() {
    logger.detachAppender(appender)
  }

  private val messages get() = appender.list.map { it.formattedMessage }

  @Nested
  inner class FeatureEnabled {
    @BeforeEach
    fun setUp() {
      whenever(eventFeatureSwitch.isEnabled("OFFENDER_SENT_CALCULATIONS-INSERTED", "calculatereleasedates")).thenReturn(true)
      whenever(eventFeatureSwitch.isEnabled("OFFENDER_SENT_CALCULATIONS-UPDATED", "calculatereleasedates")).thenReturn(true)
      whenever(eventFeatureSwitch.isEnabled("OFFENDER_SENT_CALCULATIONS-DELETED", "calculatereleasedates")).thenReturn(true)
      whenever(eventFeatureSwitch.isEnabled("OTHER_EVENT-INSERTED", "calculatereleasedates")).thenReturn(true)
    }

    @ParameterizedTest
    @ValueSource(
      strings = [
        "OFFENDER_SENT_CALCULATIONS-INSERTED",
        "OFFENDER_SENT_CALCULATIONS-UPDATED",
        "OFFENDER_SENT_CALCULATIONS-DELETED",
      ],
    )
    fun `will log each supported event type`(eventType: String) {
      listener.onMessage(calculateReleaseDatesEventMessage(eventType)).get()

      assertThat(messages).anyMatch { it.startsWith("Received calculate release dates event $eventType") }
    }

    @Test
    fun `will log an unexpected event type`() {
      listener.onMessage(calculateReleaseDatesEventMessage("OTHER_EVENT-INSERTED")).get()

      assertThat(messages).contains("Received an unexpected calculate release dates event OTHER_EVENT-INSERTED")
    }

    @Test
    fun `will log an unexpected message type`() {
      listener.onMessage("""{"Type":"Other","Message":"{}"}""").get()

      assertThat(messages).contains("Received an unexpected calculate release dates message type Other")
    }
  }

  @Nested
  inner class FeatureDisabled {
    @BeforeEach
    fun setUp() {
      whenever(eventFeatureSwitch.isEnabled("OFFENDER_SENT_CALCULATIONS-INSERTED", "calculatereleasedates")).thenReturn(false)
    }

    @Test
    fun `will skip the event`() {
      listener.onMessage(calculateReleaseDatesEventMessage("OFFENDER_SENT_CALCULATIONS-INSERTED")).get()

      assertThat(messages).containsExactly("Feature switch is disabled for calculatereleasedates event OFFENDER_SENT_CALCULATIONS-INSERTED")
    }
  }
}

fun calculateReleaseDatesEventMessage(eventType: String) = // language=JSON
  """{
    "MessageId": "ae06c49e-1f41-4b9f-b2f2-dcca610d02cd", "Type": "Notification", "Timestamp": "2019-10-21T14:01:18.500Z",
    "Message": "{\"eventType\":\"$eventType\",\"eventDatetime\":\"2024-07-10T15:00:25.489964\",\"bookingId\":123456,\"offenderIdDisplay\":\"A1234KT\"}",
    "TopicArn": "arn:aws:sns:eu-west-1:000000000000:offender_events",
    "MessageAttributes": {
      "eventType": {"Type": "String", "Value": "$eventType"},
      "id": {"Type": "String", "Value": "8b07cbd9-0820-0a0f-c32f-a9429b618e0b"},
      "contentType": {"Type": "String", "Value": "text/plain;charset=UTF-8"},
      "timestamp": {"Type": "Number.java.lang.Long", "Value": "1571666478344"}
    }
}
  """.trimIndent()
