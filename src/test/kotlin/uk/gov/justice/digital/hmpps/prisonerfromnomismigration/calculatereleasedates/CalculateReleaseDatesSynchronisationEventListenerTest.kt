package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.calculatereleasedates

import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.json.JsonTest
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.EventFeatureSwitch

@JsonTest
internal class CalculateReleaseDatesSynchronisationEventListenerTest(@Autowired private val jsonMapper: JsonMapper) {
  private val eventFeatureSwitch: EventFeatureSwitch = mock()
  private val synchronisationService: CalculateReleaseDatesSynchronisationService = mock()
  private val listener = CalculateReleaseDatesSynchronisationEventListener(jsonMapper, eventFeatureSwitch, synchronisationService)

  @Nested
  inner class FeatureEnabled {
    @BeforeEach
    fun setUp() {
      whenever(eventFeatureSwitch.isEnabled(any(), any())).thenReturn(true)
    }

    @Test
    fun `will call sentenceCalculationInserted for an inserted event`() {
      listener.onMessage(calculateReleaseDatesEventMessage("OFFENDER_SENT_CALCULATIONS-INSERTED")).get()

      runBlocking {
        verify(synchronisationService).sentenceCalculationInserted(
          check {
            assertThat(it.offenderSentCalculationId).isEqualTo(98765)
            assertThat(it.bookingId).isEqualTo(123456)
            assertThat(it.offenderIdDisplay).isEqualTo("A1234KT")
          },
        )
      }
    }

    @Test
    fun `will call sentenceCalculationUpdated for an updated event`() {
      listener.onMessage(calculateReleaseDatesEventMessage("OFFENDER_SENT_CALCULATIONS-UPDATED")).get()

      runBlocking { verify(synchronisationService).sentenceCalculationUpdated(any()) }
    }

    @Test
    fun `will call sentenceCalculationDeleted for a deleted event`() {
      listener.onMessage(calculateReleaseDatesEventMessage("OFFENDER_SENT_CALCULATIONS-DELETED")).get()

      runBlocking { verify(synchronisationService).sentenceCalculationDeleted(any()) }
    }

    @Test
    fun `will not call the service for an unexpected event type`() {
      listener.onMessage(calculateReleaseDatesEventMessage("OTHER_EVENT-INSERTED")).get()

      runBlocking {
        verify(synchronisationService, never()).sentenceCalculationInserted(any())
        verify(synchronisationService, never()).sentenceCalculationUpdated(any())
        verify(synchronisationService, never()).sentenceCalculationDeleted(any())
      }
    }
  }

  @Nested
  inner class FeatureDisabled {
    @BeforeEach
    fun setUp() {
      whenever(eventFeatureSwitch.isEnabled(any(), any())).thenReturn(false)
    }

    @Test
    fun `will not call the service`() {
      listener.onMessage(calculateReleaseDatesEventMessage("OFFENDER_SENT_CALCULATIONS-INSERTED")).get()

      runBlocking { verify(synchronisationService, never()).sentenceCalculationInserted(any()) }
    }
  }
}

fun calculateReleaseDatesEventMessage(eventType: String) = // language=JSON
  """{
    "MessageId": "ae06c49e-1f41-4b9f-b2f2-dcca610d02cd", "Type": "Notification", "Timestamp": "2019-10-21T14:01:18.500Z",
    "Message": "{\"eventType\":\"$eventType\",\"eventDatetime\":\"2024-07-10T15:00:25.489964\",\"bookingId\":123456,\"offenderIdDisplay\":\"A1234KT\",\"offenderSentCalculationId\":98765}",
    "TopicArn": "arn:aws:sns:eu-west-1:000000000000:offender_events",
    "MessageAttributes": {
      "eventType": {"Type": "String", "Value": "$eventType"},
      "id": {"Type": "String", "Value": "8b07cbd9-0820-0a0f-c32f-a9429b618e0b"},
      "contentType": {"Type": "String", "Value": "text/plain;charset=UTF-8"},
      "timestamp": {"Type": "Number.java.lang.Long", "Value": "1571666478344"}
    }
}
  """.trimIndent()
