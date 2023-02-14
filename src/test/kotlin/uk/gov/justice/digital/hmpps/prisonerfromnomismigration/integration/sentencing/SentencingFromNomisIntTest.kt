package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sentencing

import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.microsoft.applicationinsights.TelemetryClient
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilAsserted
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.springframework.boot.test.mock.mockito.SpyBean
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.purgeQueueRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.SentencingApiExtension.Companion.sentencingApi
import uk.gov.justice.hmpps.sqs.countAllMessagesOnQueue

class SentencingFromNomisIntTest : SqsIntegrationTestBase() {

  @SpyBean
  private lateinit var telemetryClient: TelemetryClient

  @BeforeEach
  fun cleanQueue() {
    awsSqsSentencingOffenderEventsClient.purgeQueue(sentencingQueueOffenderEventsUrl.purgeQueueRequest()).get()
    await untilCallTo {
      awsSqsSentencingOffenderEventsClient.countAllMessagesOnQueue(sentencingQueueOffenderEventsUrl).get()
    } matches { it == 0 }
  }

  @Nested
  @DisplayName("SENTENCE_ADJUSTMENT_UPSERTED")
  inner class SentenceAdjustmentUpserted {
    @Nested
    inner class WhenNoMappingFound {
      @BeforeEach
      fun setUp() {
        mappingApi.stubAllNomisSentencingAdjustmentsMappingNotFound()
      }

      @Nested
      inner class WhenCreateByDPS {
        @BeforeEach
        fun setUp() {
        }
      }

      @Nested
      inner class WhenCreateByNomis {
        @BeforeEach
        fun setUp() {
          nomisApi.stubGetSentenceAdjustment(adjustmentId = 987L)
          sentencingApi.stubCreateSentencingAdjustmentForSynchronisation(sentenceAdjustmentId = "123S")
          mappingApi.stubSentenceAdjustmentMappingCreate()

          awsSqsSentencingOffenderEventsClient.sendMessage(
            sentencingQueueOffenderEventsUrl,
            sentencingEvent(
              eventType = "SENTENCE_ADJUSTMENT_UPSERTED",
              auditModuleName = "OIDSENAD",
              adjustmentId = 987L
            )
          )
        }

        @Test
        fun `will retrieve mapping to check if this is a new adjustment`() {
          await untilAsserted {
            mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/sentencing/adjustments/nomis-adjustment-category/SENTENCE/nomis-adjustment-id/987")))
          }
          await untilAsserted { verify(telemetryClient).trackEvent(any(), any(), isNull()) }
        }
      }
    }

    @Nested
    inner class WhenMappingFound {
      @BeforeEach
      fun setUp() {
        mappingApi.stubGetNomisSentencingAdjustment(adjustmentCategory = "SENTENCE", nomisAdjustmentId = 987)
      }

      @Nested
      inner class WhenUpdatedByDPS {
        @BeforeEach
        fun setUp() {
        }
      }

      @Nested
      inner class WhenUpdatedByNomis {
        @BeforeEach
        fun setUp() {
          nomisApi.stubGetSentenceAdjustment(adjustmentId = 987L)
          sentencingApi.stubUpdateSentencingAdjustmentForSynchronisation()

          awsSqsSentencingOffenderEventsClient.sendMessage(
            sentencingQueueOffenderEventsUrl,
            sentencingEvent(
              eventType = "SENTENCE_ADJUSTMENT_UPSERTED",
              auditModuleName = "OIDSENAD",
              adjustmentId = 987L
            )
          )
        }

        @Test
        fun `will retrieve mapping to check if this is an updated adjustment`() {
          await untilAsserted {
            mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/sentencing/adjustments/nomis-adjustment-category/SENTENCE/nomis-adjustment-id/987")))
          }
          await untilAsserted { verify(telemetryClient).trackEvent(any(), any(), isNull()) }
        }
      }
    }
  }
}

fun sentencingEvent(
  eventType: String,
  offenderIdDisplay: String = "G4803UT",
  bookingId: Long = 1234,
  sentenceSeq: Long? = 1,
  adjustmentId: Long = 9876,
  auditModuleName: String = "OIDSENAD"
) = """{
    "MessageId": "ae06c49e-1f41-4b9f-b2f2-dcca610d02cd", "Type": "Notification", "Timestamp": "2019-10-21T14:01:18.500Z", 
    "Message": "{\"eventId\":\"5958295\",\"eventType\":\"$eventType\",\"eventDatetime\":\"2019-10-21T15:00:25.489964\",\"offenderIdDisplay\":\"$offenderIdDisplay\",\"bookingId\": \"$bookingId\",${sentenceSeq.asJson()}\"nomisEventType\":\"WHATEVER\",\"adjustmentId\":\"$adjustmentId\",\"auditModuleName\":\"$auditModuleName\" }",
    "TopicArn": "arn:aws:sns:eu-west-1:000000000000:offender_events", 
    "MessageAttributes": {
      "eventType": {"Type": "String", "Value": "$eventType"}, 
      "id": {"Type": "String", "Value": "8b07cbd9-0820-0a0f-c32f-a9429b618e0b"}, 
      "contentType": {"Type": "String", "Value": "text/plain;charset=UTF-8"}, 
      "timestamp": {"Type": "Number.java.lang.Long", "Value": "1571666478344"}
    }
}
""".trimIndent()

private fun Long?.asJson() = if (this == null) "" else """ \"sentenceSeq\":\"$this\", """
