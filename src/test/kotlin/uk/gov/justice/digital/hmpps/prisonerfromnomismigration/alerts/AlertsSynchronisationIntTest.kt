package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.exactly
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilAsserted
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito.eq
import org.mockito.kotlin.check
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.http.HttpStatus.NOT_FOUND
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts.AlertsDpsApiExtension.Companion.dpsAlertsServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts.model.NomisAlertMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts.model.NomisAlertMapping.Status.CREATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.AlertResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.NomisAudit
import uk.gov.justice.hmpps.sqs.countAllMessagesOnQueue
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.AbstractMap.SimpleEntry
import java.util.UUID

private const val BOOKING_ID = 1234L
private const val ALERT_SEQUENCE = 1L

class AlertsSynchronisationIntTest : SqsIntegrationTestBase() {
  @Autowired
  private lateinit var alertsNomisApiMockServer: AlertsNomisApiMockServer

  @Autowired
  private lateinit var alertsMappingApiMockServer: AlertsMappingApiMockServer

  @Nested
  @DisplayName("ALERT-INSERTED")
  inner class AlertInserted {

    @Nested
    @DisplayName("When alert was created in DPS")
    inner class DPSCreated {
      private val bookingId = 12345L
      private val alertSequence = 3L

      @BeforeEach
      fun setUp() {
        alertsNomisApiMockServer.stubGetAlert(
          bookingId = bookingId,
          alertSequence = alertSequence,
          alert = alert(bookingId = bookingId, alertSequence = alertSequence).copy(
            audit = alert().audit.copy(
              auditModuleName = "DPS_SYNCHRONISATION",
            ),
          ),
        )
        awsSqsAlertOffenderEventsClient.sendMessage(
          alertsQueueOffenderEventsUrl,
          alertEvent(
            eventType = "ALERT-INSERTED",
            bookingId = bookingId,
            alertSequence = alertSequence,
          ),
        )
      }

      @Test
      fun `the event is ignored`() {
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("alert-synchronisation-skipped"),
            check {
              // assertThat(it["offenderNo"]).isEqualTo("???")  // TODO - not in event right now
              assertThat(it["bookingId"]).isEqualTo(bookingId.toString())
              assertThat(it["alertSequence"]).isEqualTo(alertSequence.toString())
            },
            isNull(),
          )
        }

        // will not bother getting mapping
        alertsMappingApiMockServer.verify(
          count = 0,
          getRequestedFor(urlPathMatching("/mapping/alerts/nomis-booking-id/\\d+/nomis-alert-sequence/\\d+")),
        )
        // will not create an alert in DPS
        dpsAlertsServer.verify(0, postRequestedFor(anyUrl()))
      }
    }

    @Nested
    @DisplayName("When alert was created in NOMIS")
    inner class NomisCreated {
      private val bookingId = 12345L
      private val alertSequence = 3L

      @BeforeEach
      fun setUp() {
        alertsNomisApiMockServer.stubGetAlert(
          bookingId = bookingId,
          alertSequence = alertSequence,
          alert = alert(bookingId = bookingId, alertSequence = alertSequence).copy(
            alertCode = CodeDescription("XNR", "Not For Release"),
            type = CodeDescription("X", "Security"),
            audit = alert().audit.copy(
              auditModuleName = "OCDALERT",
            ),
          ),
        )
      }

      @Nested
      @DisplayName("When mapping does not exist yet")
      inner class NoMapping {
        private val dpsAlertId = "a04f7a8d-61aa-400c-9395-f4dc62f36ab0"

        @BeforeEach
        fun setUp() {
          alertsMappingApiMockServer.stubGetByNomisId(status = NOT_FOUND)
          dpsAlertsServer.stubPostAlertForCreate(
            NomisAlertMapping(
              offenderBookId = bookingId,
              alertSeq = alertSequence.toInt(),
              alertUuid = UUID.fromString(dpsAlertId),
              status = CREATED,
            ),
          )
          alertsMappingApiMockServer.stubPostMapping()
          awsSqsAlertOffenderEventsClient.sendMessage(
            alertsQueueOffenderEventsUrl,
            alertEvent(
              eventType = "ALERT-INSERTED",
              bookingId = bookingId,
              alertSequence = alertSequence,
            ),
          )
        }

        @Test
        fun `will create alert in DPS`() {
          await untilAsserted {
            dpsAlertsServer.verify(
              postRequestedFor(urlPathEqualTo("/nomis-alerts"))
                .withRequestBody(matchingJsonPath("alertCode", equalTo("XNR")))
                .withRequestBody(matchingJsonPath("alertType", equalTo("X"))),
            )
          }
        }

        @Test
        fun `will create mapping between DPS and NOMIS ids`() {
          await untilAsserted {
            alertsMappingApiMockServer.verify(
              postRequestedFor(urlPathEqualTo("/mapping/alerts"))
                .withRequestBody(matchingJsonPath("dpsAlertId", equalTo(dpsAlertId)))
                .withRequestBody(matchingJsonPath("nomisBookingId", equalTo(bookingId.toString())))
                .withRequestBody(matchingJsonPath("nomisAlertSequence", equalTo(alertSequence.toString())))
                .withRequestBody(matchingJsonPath("mappingType", equalTo("DPS_CREATED"))),
            )
          }
        }

        @Test
        fun `will track a telemetry event for success`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("alert-synchronisation-created-synchronisation-success"),
              check {
                // assertThat(it["offenderNo"]).isEqualTo("???")  // TODO - not in event right now
                assertThat(it["bookingId"]).isEqualTo(bookingId.toString())
                assertThat(it["alertSequence"]).isEqualTo(alertSequence.toString())
                assertThat(it).doesNotContain(SimpleEntry("mapping", "initial-failure"))
              },
              isNull(),
            )
          }
        }
      }

      @Nested
      @DisplayName("When mapping POST fails")
      inner class MappingFail {
        private val dpsAlertId = "a04f7a8d-61aa-400c-9395-f4dc62f36ab0"

        @BeforeEach
        fun setUp() {
          alertsMappingApiMockServer.stubGetByNomisId(status = NOT_FOUND)
          dpsAlertsServer.stubPostAlertForCreate(
            NomisAlertMapping(
              offenderBookId = bookingId,
              alertSeq = alertSequence.toInt(),
              alertUuid = UUID.fromString(dpsAlertId),
              status = CREATED,
            ),
          )
        }

        @Nested
        @DisplayName("Fails once")
        inner class FailsOnce {
          @BeforeEach
          fun setUp() {
            alertsMappingApiMockServer.stubPostMappingFailureFollowedBySuccess()
            awsSqsAlertOffenderEventsClient.sendMessage(
              alertsQueueOffenderEventsUrl,
              alertEvent(
                eventType = "ALERT-INSERTED",
                bookingId = bookingId,
                alertSequence = alertSequence,
              ),
            )
          }

          @Test
          fun `will create alert in DPS`() {
            await untilAsserted {
              dpsAlertsServer.verify(
                postRequestedFor(urlPathEqualTo("/nomis-alerts"))
                  .withRequestBody(matchingJsonPath("alertCode", equalTo("XNR")))
                  .withRequestBody(matchingJsonPath("alertType", equalTo("X"))),
              )
            }
          }

          @Test
          fun `will attempt to create mapping two times and succeed`() {
            await untilAsserted {
              alertsMappingApiMockServer.verify(
                exactly(2),
                postRequestedFor(urlPathEqualTo("/mapping/alerts"))
                  .withRequestBody(matchingJsonPath("dpsAlertId", equalTo(dpsAlertId)))
                  .withRequestBody(matchingJsonPath("nomisBookingId", equalTo(bookingId.toString())))
                  .withRequestBody(matchingJsonPath("nomisAlertSequence", equalTo(alertSequence.toString())))
                  .withRequestBody(matchingJsonPath("mappingType", equalTo("DPS_CREATED"))),
              )
            }

            assertThat(
              awsSqsAlertsOffenderEventDlqClient.countAllMessagesOnQueue(alertsQueueOffenderEventsDlqUrl).get(),
            ).isEqualTo(0)
          }

          @Test
          fun `will track a telemetry event for partial success`() {
            await untilAsserted {
              verify(telemetryClient).trackEvent(
                eq("alert-synchronisation-created-synchronisation-success"),
                check {
                  // assertThat(it["offenderNo"]).isEqualTo("???")  // TODO - not in event right now
                  assertThat(it["bookingId"]).isEqualTo(bookingId.toString())
                  assertThat(it["alertSequence"]).isEqualTo(alertSequence.toString())
                  assertThat(it["mapping"]).isEqualTo("initial-failure")
                },
                isNull(),
              )
            }

            await untilAsserted {
              verify(telemetryClient).trackEvent(
                eq("alert-mapping-created-synchronisation-success"),
                check {
                  // assertThat(it["offenderNo"]).isEqualTo("???")  // TODO - not in event right now
                  assertThat(it["bookingId"]).isEqualTo(bookingId.toString())
                  assertThat(it["alertSequence"]).isEqualTo(alertSequence.toString())
                },
                isNull(),
              )
            }
          }
        }

        @Nested
        @DisplayName("Fails constantly")
        inner class FailsConstantly {
          @BeforeEach
          fun setUp() {
            alertsMappingApiMockServer.stubPostMapping(status = INTERNAL_SERVER_ERROR)
            awsSqsAlertOffenderEventsClient.sendMessage(
              alertsQueueOffenderEventsUrl,
              alertEvent(
                eventType = "ALERT-INSERTED",
                bookingId = bookingId,
                alertSequence = alertSequence,
              ),
            )
            await untilCallTo {
              awsSqsAlertsOffenderEventDlqClient.countAllMessagesOnQueue(alertsQueueOffenderEventsDlqUrl).get()
            } matches { it == 1 }
          }

          @Test
          fun `will create alert in DPS`() {
            await untilAsserted {
              dpsAlertsServer.verify(
                1,
                postRequestedFor(urlPathEqualTo("/nomis-alerts"))
                  .withRequestBody(matchingJsonPath("alertCode", equalTo("XNR")))
                  .withRequestBody(matchingJsonPath("alertType", equalTo("X"))),
              )
            }
          }

          @Test
          fun `will attempt to create mapping several times and keep failing`() {
            alertsMappingApiMockServer.verify(
              exactly(3),
              postRequestedFor(urlPathEqualTo("/mapping/alerts")),
            )
          }

          @Test
          fun `will track a telemetry event for success`() {
            await untilAsserted {
              verify(telemetryClient).trackEvent(
                eq("alert-synchronisation-created-synchronisation-success"),
                check {
                  // assertThat(it["offenderNo"]).isEqualTo("???")  // TODO - not in event right now
                  assertThat(it["bookingId"]).isEqualTo(bookingId.toString())
                  assertThat(it["alertSequence"]).isEqualTo(alertSequence.toString())
                  assertThat(it["mapping"]).isEqualTo("initial-failure")
                },
                isNull(),
              )
            }
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
  alertSequence: Long = ALERT_SEQUENCE,
) = """{
    "MessageId": "ae06c49e-1f41-4b9f-b2f2-dcca610d02cd", "Type": "Notification", "Timestamp": "2019-10-21T14:01:18.500Z", 
    "Message": "{\"eventId\":\"5958295\",\"eventType\":\"$eventType\",\"eventDatetime\":\"2019-10-21T15:00:25.489964\",\"bookingId\": \"$bookingId\",\"alertSeq\": \"$alertSequence\",\"nomisEventType\":\"OFF_ALERT_UPDATE\",\"alertType\":\"L\",\"alertCode\":\"LCE\",\"alertDateTime\":\"2024-02-14T13:24:11\" }",
    "TopicArn": "arn:aws:sns:eu-west-1:000000000000:offender_events", 
    "MessageAttributes": {
      "eventType": {"Type": "String", "Value": "$eventType"}, 
      "id": {"Type": "String", "Value": "8b07cbd9-0820-0a0f-c32f-a9429b618e0b"}, 
      "contentType": {"Type": "String", "Value": "text/plain;charset=UTF-8"}, 
      "timestamp": {"Type": "Number.java.lang.Long", "Value": "1571666478344"}
    }
}
""".trimIndent()

private fun alert(bookingId: Long = 123456, alertSequence: Long = 3) = AlertResponse(
  bookingId = bookingId,
  alertSequence = alertSequence,
  alertCode = CodeDescription("XA", "TACT"),
  type = CodeDescription("X", "Security"),
  date = LocalDate.now(),
  isActive = true,
  isVerified = false,
  audit = NomisAudit(
    createDatetime = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
    createUsername = "Q1251T",
  ),
)
