package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.agency

import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.json.JsonTest
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.EventFeatureSwitch

@JsonTest
internal class AgencyRegistersEventListenerTest(@Autowired private val jsonMapper: JsonMapper) {
  private val eventFeatureSwitch: EventFeatureSwitch = mock()
  private val agencyRegistersSynchronisationService: AgencyRegistersSynchronisationService = mock()

  private val listener = AgencyRegistersEventListener(
    jsonMapper = jsonMapper,
    eventFeatureSwitch = eventFeatureSwitch,
    agencyRegistersSynchronisationService = agencyRegistersSynchronisationService,
  )

  @BeforeEach
  fun setUp() {
    whenever(eventFeatureSwitch.isEnabled(any(), any())).thenReturn(true)
  }

  @ParameterizedTest
  @ValueSource(
    strings = [
      "ADDRESSES_AGENCY-INSERTED",
      "ADDRESSES_AGENCY-UPDATED",
      "ADDRESSES_AGENCY-DELETED",
      "AGENCY_LOCATIONS-INSERTED",
      "AGENCY_LOCATIONS-UPDATED",
      "AGENCY_LOCATION-AUTHORITIES-INSERTED",
      "AGENCY_LOCATION-AUTHORITIES-UPDATED",
      "AGENCY_LOCATION-AUTHORITIES-DELETED",
      "PHONES_AGENCY-INSERTED",
      "PHONES_AGENCY-UPDATED",
      "PHONES_AGENCY-DELETED",
      "INTERNET_ADDRESSES_AGENCY-INSERTED",
      "INTERNET_ADDRESSES_AGENCY-UPDATED",
      "INTERNET_ADDRESSES_AGENCY-DELETED",
    ],
  )
  fun `will call agencyUpdated for each supported event type`(eventType: String) {
    listener.onMessage(agencyRegisterEventMessage(eventType = eventType, agencyCode = "SHEFCC")).get()

    runBlocking {
      verify(agencyRegistersSynchronisationService).agencyUpdated(
        check {
          assertThat(it.agencyCode).isEqualTo("SHEFCC")
        },
      )
    }
  }

  @Test
  fun `will not call agencyUpdated for an event type it does not recognise`() {
    listener.onMessage(agencyRegisterEventMessage(eventType = "OTHER_EVENT-INSERTED", agencyCode = "SHEFCC")).get()

    runBlocking {
      verify(agencyRegistersSynchronisationService, never()).agencyUpdated(any())
    }
  }

  @Test
  fun `will throw and not call agencyUpdated for AGENCY_LOCATIONS-DELETED`() {
    val future =
      listener.onMessage(agencyRegisterEventMessage(eventType = "AGENCY_LOCATIONS-DELETED", agencyCode = "SHEFCC"))

    assertThatThrownBy { future.get() }
      .cause()
      .isInstanceOf(UnsupportedOperationException::class.java)
      .hasMessage("AGENCY_LOCATIONS-DELETED event is not supported")

    runBlocking {
      verify(agencyRegistersSynchronisationService, never()).agencyUpdated(any())
    }
  }

  @Test
  fun `will not call agencyUpdated when the feature switch is disabled`() {
    whenever(eventFeatureSwitch.isEnabled(eq("ADDRESSES_AGENCY-UPDATED"), any())).thenReturn(false)

    listener.onMessage(agencyRegisterEventMessage(eventType = "ADDRESSES_AGENCY-UPDATED", agencyCode = "SHEFCC")).get()

    runBlocking {
      verify(agencyRegistersSynchronisationService, never()).agencyUpdated(any())
    }
  }
}

fun agencyRegisterEventMessage(
  eventType: String,
  agencyCode: String,
  auditModuleName: String = "OCUAGY",
) = // language=JSON
  """{
    "MessageId": "ae06c49e-1f41-4b9f-b2f2-dcca610d02cd", "Type": "Notification", "Timestamp": "2019-10-21T14:01:18.500Z", 
    "Message": "{\"eventType\":\"$eventType\",\"eventDatetime\":\"2019-10-21T15:00:25.489964\",\"agencyCode\": \"$agencyCode\",\"auditModuleName\":\"$auditModuleName\",\"nomisEventType\":\"$eventType\" }",
    "TopicArn": "arn:aws:sns:eu-west-1:000000000000:offender_events", 
    "MessageAttributes": {
      "eventType": {"Type": "String", "Value": "$eventType"}, 
      "id": {"Type": "String", "Value": "8b07cbd9-0820-0a0f-c32f-a9429b618e0b"}, 
      "contentType": {"Type": "String", "Value": "text/plain;charset=UTF-8"}, 
      "timestamp": {"Type": "Number.java.lang.Long", "Value": "1571666478344"}
    }
}
  """.trimIndent()
