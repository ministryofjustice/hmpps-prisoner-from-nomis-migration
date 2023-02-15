package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing

import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilAsserted
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.purgeQueueRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.SentencingApiExtension.Companion.sentencingApi
import uk.gov.justice.hmpps.sqs.countAllMessagesOnQueue

private const val NOMIS_ADJUSTMENT_ID = 987L
private const val ADJUSTMENT_ID = "05b332ad-58eb-4ec2-963c-c9c927856788"
private const val OFFENDER_NUMBER = "G4803UT"
private const val BOOKING_ID = 1234L
private const val SENTENCE_SEQUENCE = 1L

class SentencingFromNomisIntTest : SqsIntegrationTestBase() {

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
          nomisApi.stubGetSentenceAdjustment(adjustmentId = NOMIS_ADJUSTMENT_ID)
          sentencingApi.stubCreateSentencingAdjustmentForSynchronisation(sentenceAdjustmentId = ADJUSTMENT_ID)
          mappingApi.stubSentenceAdjustmentMappingCreate()

          awsSqsSentencingOffenderEventsClient.sendMessage(
            sentencingQueueOffenderEventsUrl,
            sentencingEvent(
              eventType = "SENTENCE_ADJUSTMENT_UPSERTED",
              auditModuleName = "OIDSENAD",
              adjustmentId = NOMIS_ADJUSTMENT_ID,
              bookingId = BOOKING_ID,
              sentenceSeq = SENTENCE_SEQUENCE,
              offenderIdDisplay = OFFENDER_NUMBER,
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

        @Test
        fun `will retrieve details about the adjustment from NOMIS`() {
          await untilAsserted {
            nomisApi.verify(getRequestedFor(urlPathEqualTo("/sentence-adjustments/$NOMIS_ADJUSTMENT_ID")))
          }
          await untilAsserted { verify(telemetryClient).trackEvent(any(), any(), isNull()) }
        }

        @Test
        fun `will create the adjustment in the sentencing service`() {
          await untilAsserted {
            sentencingApi.verify(postRequestedFor(urlPathEqualTo("/synchronisation/sentencing/adjustments")))
          }
          await untilAsserted { verify(telemetryClient).trackEvent(any(), any(), isNull()) }
        }

        @Test
        fun `will create a mapping between the two records`() {
          await untilAsserted {
            mappingApi.verify(
              postRequestedFor(urlPathEqualTo("/mapping/sentencing/adjustments"))
                .withRequestBody(matchingJsonPath("nomisAdjustmentId", equalTo(NOMIS_ADJUSTMENT_ID.toString())))
                .withRequestBody(matchingJsonPath("nomisAdjustmentCategory", equalTo("SENTENCE")))
                .withRequestBody(matchingJsonPath("adjustmentId", equalTo(ADJUSTMENT_ID)))
            )
          }
          await untilAsserted { verify(telemetryClient).trackEvent(any(), any(), isNull()) }
        }

        @Test
        fun `will create telemetry tracking the create`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              Mockito.eq("sentence-adjustment-created-synchronisation-success"),
              org.mockito.kotlin.check {
                assertThat(it["adjustmentId"]).isEqualTo(ADJUSTMENT_ID)
                assertThat(it["adjustmentCategory"]).isEqualTo("SENTENCE")
                assertThat(it["nomisAdjustmentId"]).isEqualTo(NOMIS_ADJUSTMENT_ID.toString())
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NUMBER)
                assertThat(it["bookingId"]).isEqualTo(BOOKING_ID.toString())
                assertThat(it["sentenceSequence"]).isEqualTo(SENTENCE_SEQUENCE.toString())
              },
              isNull(),
            )
          }
        }
      }
    }

    @Nested
    inner class WhenMappingFound {
      @BeforeEach
      fun setUp() {
        mappingApi.stubGetNomisSentencingAdjustment(
          adjustmentCategory = "SENTENCE",
          nomisAdjustmentId = NOMIS_ADJUSTMENT_ID,
          adjustmentId = ADJUSTMENT_ID,
        )
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
          nomisApi.stubGetSentenceAdjustment(adjustmentId = NOMIS_ADJUSTMENT_ID)
          sentencingApi.stubUpdateSentencingAdjustmentForSynchronisation(ADJUSTMENT_ID)

          awsSqsSentencingOffenderEventsClient.sendMessage(
            sentencingQueueOffenderEventsUrl,
            sentencingEvent(
              eventType = "SENTENCE_ADJUSTMENT_UPSERTED",
              auditModuleName = "OIDSENAD",
              adjustmentId = NOMIS_ADJUSTMENT_ID,
              bookingId = BOOKING_ID,
              sentenceSeq = SENTENCE_SEQUENCE,
              offenderIdDisplay = OFFENDER_NUMBER,
            )
          )
        }

        @Test
        fun `will retrieve mapping to check if this is an updated adjustment`() {
          await untilAsserted {
            mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/sentencing/adjustments/nomis-adjustment-category/SENTENCE/nomis-adjustment-id/$NOMIS_ADJUSTMENT_ID")))
          }
          await untilAsserted { verify(telemetryClient).trackEvent(any(), any(), isNull()) }
        }

        @Test
        fun `will retrieve details about the adjustment from NOMIS`() {
          await untilAsserted {
            nomisApi.verify(getRequestedFor(urlPathEqualTo("/sentence-adjustments/$NOMIS_ADJUSTMENT_ID")))
          }
          await untilAsserted { verify(telemetryClient).trackEvent(any(), any(), isNull()) }
        }

        @Test
        fun `will update the adjustment in the sentencing service`() {
          await untilAsserted {
            sentencingApi.verify(putRequestedFor(urlPathEqualTo("/synchronisation/sentencing/adjustments/$ADJUSTMENT_ID")))
          }
          await untilAsserted { verify(telemetryClient).trackEvent(any(), any(), isNull()) }
        }

        @Test
        fun `will create telemetry tracking the update`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              Mockito.eq("sentence-adjustment-updated-synchronisation-success"),
              org.mockito.kotlin.check {
                assertThat(it["adjustmentId"]).isEqualTo(ADJUSTMENT_ID)
                assertThat(it["adjustmentCategory"]).isEqualTo("SENTENCE")
                assertThat(it["nomisAdjustmentId"]).isEqualTo(NOMIS_ADJUSTMENT_ID.toString())
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NUMBER)
                assertThat(it["bookingId"]).isEqualTo(BOOKING_ID.toString())
                assertThat(it["sentenceSequence"]).isEqualTo(SENTENCE_SEQUENCE.toString())
              },
              isNull(),
            )
          }
        }
      }
    }
  }
}

fun sentencingEvent(
  eventType: String,
  offenderIdDisplay: String = OFFENDER_NUMBER,
  bookingId: Long = BOOKING_ID,
  sentenceSeq: Long? = SENTENCE_SEQUENCE,
  adjustmentId: Long = NOMIS_ADJUSTMENT_ID,
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
