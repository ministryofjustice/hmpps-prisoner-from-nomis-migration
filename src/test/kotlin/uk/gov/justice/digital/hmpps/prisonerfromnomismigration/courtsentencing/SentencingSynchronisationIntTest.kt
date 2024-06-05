package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
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
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.check
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.mockito.verification.VerificationMode
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatus.NOT_FOUND
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.CourtSentencingDpsApiExtension.Companion.dpsCourtSentencingServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.SentenceAllMappingDto
import uk.gov.justice.hmpps.sqs.countAllMessagesOnQueue
import java.util.AbstractMap.SimpleEntry

private const val NOMIS_SENTENCE_SEQUENCE = 1
private const val DPS_SENTENCE_ID = "cc1"
private const val EXISTING_DPS_SENTENCE_ID = "cc2"
private const val OFFENDER_ID_DISPLAY = "A3864DZ"
private const val NOMIS_BOOKING_ID = 12344321L

class SentencingSynchronisationIntTest : SqsIntegrationTestBase() {
  @Autowired
  private lateinit var courtSentencingNomisApiMockServer: CourtSentencingNomisApiMockServer

  @Autowired
  private lateinit var courtSentencingMappingApiMockServer: CourtSentencingMappingApiMockServer

  @Nested
  @DisplayName("OFFENDER_SENTENCES-INSERTED")
  inner class OffenderSentenceInserted {

    @Nested
    @DisplayName("When sentencing was created in DPS")
    inner class DPSCreated {

      @BeforeEach
      fun setUp() {
        courtSentencingNomisApiMockServer.stubGetSentence(
          sentenceSequence = NOMIS_SENTENCE_SEQUENCE,
          bookingId = NOMIS_BOOKING_ID,
          offenderNo = OFFENDER_ID_DISPLAY,
        )
        awsSqsCourtSentencingOffenderEventsClient.sendMessage(
          courtSentencingQueueOffenderEventsUrl,
          sentenceEvent(
            eventType = "OFFENDER_SENTENCES-INSERTED",
            auditModule = "DPS_SYNCHRONISATION",
          ),
        ).also {
          waitForTelemetry()
        }
      }

      @Test
      fun `the event is ignored`() {
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("sentence-synchronisation-created-skipped"),
            check {
              assertThat(it["offenderNo"]).isEqualTo("A3864DZ")
              assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
              assertThat(it["nomisSentenceSequence"]).isEqualTo(NOMIS_SENTENCE_SEQUENCE.toString())
            },
            isNull(),
          )
        }

        courtSentencingMappingApiMockServer.verify(
          0,
          getRequestedFor(urlPathMatching("/mapping/court-sentencing/sentences/nomis-booking-id/\\d+/nomis-sentence-sequence/\\d+")),
        )
        // will not create an sentence in DPS
        dpsCourtSentencingServer.verify(0, postRequestedFor(anyUrl()))
      }
    }

    @Nested
    @DisplayName("When sentence was created in NOMIS")
    inner class NomisCreated {
      @BeforeEach
      fun setUp() {
        courtSentencingNomisApiMockServer.stubGetSentence(
          bookingId = NOMIS_BOOKING_ID,
          sentenceSequence = NOMIS_SENTENCE_SEQUENCE,
        )
      }

      @Nested
      @DisplayName("When mapping does not exist yet")
      inner class NoMapping {
        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubGetSentenceByNomisId(status = NOT_FOUND)
          dpsCourtSentencingServer.stubPostSentenceForCreate(sentenceId = DPS_SENTENCE_ID)
          courtSentencingMappingApiMockServer.stubPostSentenceMapping()
          awsSqsCourtSentencingOffenderEventsClient.sendMessage(
            courtSentencingQueueOffenderEventsUrl,
            sentenceEvent(
              eventType = "OFFENDER_SENTENCES-INSERTED",
            ),
          ).also {
            waitForTelemetry()
          }
        }

        @Test
        fun `will create a sentence in DPS`() {
          await untilAsserted {
            dpsCourtSentencingServer.verify(
              postRequestedFor(urlPathEqualTo("/sentence")),
              // TODO assert once DPS team have defined their dto
            )
          }
        }

        @Test
        fun `will create mapping between DPS and NOMIS ids`() {
          await untilAsserted {
            courtSentencingMappingApiMockServer.verify(
              postRequestedFor(urlPathEqualTo("/mapping/court-sentencing/sentences"))
                .withRequestBody(matchingJsonPath("dpsSentenceId", equalTo(DPS_SENTENCE_ID)))
                .withRequestBody(matchingJsonPath("nomisSentenceSequence", equalTo(NOMIS_SENTENCE_SEQUENCE.toString())))
                .withRequestBody(matchingJsonPath("nomisBookingId", equalTo(NOMIS_BOOKING_ID.toString())))
                .withRequestBody(matchingJsonPath("mappingType", equalTo("NOMIS_CREATED"))),
            )
          }
        }

        @Test
        fun `will track a telemetry event for success`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("sentence-synchronisation-created-success"),
              check {
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
                assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
                assertThat(it["nomisSentenceSequence"]).isEqualTo(NOMIS_SENTENCE_SEQUENCE.toString())
                assertThat(it["dpsSentenceId"]).isEqualTo(DPS_SENTENCE_ID)
                assertThat(it).doesNotContain(SimpleEntry("mapping", "initial-failure"))
              },
              isNull(),
            )
          }
        }
      }

      @Nested
      @DisplayName("When mapping already exists")
      inner class MappingExists {
        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubGetSentenceByNomisId(
            nomisSentenceSequence = NOMIS_SENTENCE_SEQUENCE,
            nomisBookingId = NOMIS_BOOKING_ID,
            dpsSentenceId = DPS_SENTENCE_ID,
            mapping = SentenceAllMappingDto(
              nomisSentenceSequence = NOMIS_SENTENCE_SEQUENCE,
              nomisBookingId = NOMIS_BOOKING_ID,
              dpsSentenceId = DPS_SENTENCE_ID,
              sentenceCharges = emptyList(),
            ),
          )
          awsSqsCourtSentencingOffenderEventsClient.sendMessage(
            courtSentencingQueueOffenderEventsUrl,
            sentenceEvent(
              eventType = "OFFENDER_SENTENCES-INSERTED",
            ),
          ).also {
            waitForTelemetry()
          }
        }

        @Test
        fun `the event is ignored`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("sentence-synchronisation-created-ignored"),
              check {
                assertThat(it["offenderNo"]).isEqualTo("A3864DZ")
                assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
                assertThat(it["nomisSentenceSequence"]).isEqualTo(NOMIS_SENTENCE_SEQUENCE.toString())
              },
              isNull(),
            )
          }
          // will not create a sentence in DPS
          dpsCourtSentencingServer.verify(0, postRequestedFor(anyUrl()))
        }
      }

      @Nested
      @DisplayName("When mapping POST fails")
      inner class MappingFail {
        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubGetSentenceByNomisId(status = NOT_FOUND)
          dpsCourtSentencingServer.stubPostSentenceForCreate(sentenceId = DPS_SENTENCE_ID)
        }

        @Nested
        @DisplayName("Fails once")
        inner class FailsOnce {
          @BeforeEach
          fun setUp() {
            courtSentencingMappingApiMockServer.stubPostSentenceMappingFailureFollowedBySuccess()
            awsSqsCourtSentencingOffenderEventsClient.sendMessage(
              courtSentencingQueueOffenderEventsUrl,
              sentenceEvent(
                eventType = "OFFENDER_SENTENCES-INSERTED",
              ),
            ).also {
              waitForTelemetry()
            }
          }

          @Test
          fun `will create a sentence in DPS`() {
            await untilAsserted {
              dpsCourtSentencingServer.verify(
                postRequestedFor(urlPathEqualTo("/sentence")),
              )
            }
          }

          @Test
          fun `will attempt to create mapping two times and succeed`() {
            await untilAsserted {
              courtSentencingMappingApiMockServer.verify(
                WireMock.exactly(2),
                postRequestedFor(urlPathEqualTo("/mapping/court-sentencing/sentences"))
                  .withRequestBody(matchingJsonPath("dpsSentenceId", equalTo(DPS_SENTENCE_ID)))
                  .withRequestBody(matchingJsonPath("nomisSentenceSequence", equalTo(NOMIS_SENTENCE_SEQUENCE.toString())))
                  .withRequestBody(matchingJsonPath("nomisBookingId", equalTo(NOMIS_BOOKING_ID.toString())))
                  .withRequestBody(matchingJsonPath("mappingType", equalTo("NOMIS_CREATED"))),
              )
            }

            assertThat(
              awsSqsCourtSentencingOffenderEventDlqClient.countAllMessagesOnQueue(
                courtSentencingQueueOffenderEventsDlqUrl,
              ).get(),
            ).isEqualTo(0)
          }

          @Test
          fun `will track a telemetry event for partial success`() {
            await untilAsserted {
              verify(telemetryClient).trackEvent(
                eq("sentence-synchronisation-created-success"),
                check {
                  assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
                  assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
                  assertThat(it["nomisSentenceSequence"]).isEqualTo(NOMIS_SENTENCE_SEQUENCE.toString())
                  assertThat(it["dpsSentenceId"]).isEqualTo(DPS_SENTENCE_ID)
                  assertThat(it["mapping"]).isEqualTo("initial-failure")
                },
                isNull(),
              )
            }

            await untilAsserted {
              verify(telemetryClient).trackEvent(
                eq("sentence-mapping-created-synchronisation-success"),
                check {
                  assertThat(it["offenderNo"]).isEqualTo("A3864DZ")
                  assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
                  assertThat(it["nomisSentenceSequence"]).isEqualTo(NOMIS_SENTENCE_SEQUENCE.toString())
                  assertThat(it["dpsSentenceId"]).isEqualTo(DPS_SENTENCE_ID)
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
            courtSentencingMappingApiMockServer.stubPostSentenceMapping(status = HttpStatus.INTERNAL_SERVER_ERROR)
            awsSqsCourtSentencingOffenderEventsClient.sendMessage(
              courtSentencingQueueOffenderEventsUrl,
              sentenceEvent(
                eventType = "OFFENDER_SENTENCES-INSERTED",
              ),
            )
            await untilCallTo {
              awsSqsCourtSentencingOffenderEventDlqClient.countAllMessagesOnQueue(
                courtSentencingQueueOffenderEventsDlqUrl,
              ).get()
            } matches { it == 1 }
          }

          @Test
          fun `will create a sentence in DPS`() {
            await untilAsserted {
              dpsCourtSentencingServer.verify(
                1,
                postRequestedFor(urlPathEqualTo("/sentence")),
              )
            }
          }

          @Test
          fun `will attempt to create mapping several times and keep failing`() {
            courtSentencingMappingApiMockServer.verify(
              WireMock.exactly(3),
              postRequestedFor(urlPathEqualTo("/mapping/court-sentencing/sentences")),
            )
          }

          @Test
          fun `will track a telemetry event for success`() {
            await untilAsserted {
              verify(telemetryClient).trackEvent(
                eq("sentence-synchronisation-created-success"),
                check {
                  assertThat(it["offenderNo"]).isEqualTo("A3864DZ")
                  assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
                  assertThat(it["nomisSentenceSequence"]).isEqualTo(NOMIS_SENTENCE_SEQUENCE.toString())
                  assertThat(it["dpsSentenceId"]).isEqualTo(DPS_SENTENCE_ID)
                  assertThat(it["mapping"]).isEqualTo("initial-failure")
                },
                isNull(),
              )
            }
          }
        }
      }
    }

    @Nested
    @DisplayName("duplicate mapping - two messages received at the same time")
    inner class WhenDuplicate {

      @Test
      internal fun `it will not retry after a 409 (duplicate sentence written to Sentencing API)`() {
        // in the case of multiple events received at the same time - mapping doesn't exist
        courtSentencingMappingApiMockServer.stubGetByNomisId(status = NOT_FOUND)

        courtSentencingNomisApiMockServer.stubGetSentence(
          bookingId = NOMIS_BOOKING_ID,
          sentenceSequence = NOMIS_SENTENCE_SEQUENCE,
        )
        dpsCourtSentencingServer.stubPostSentenceForCreate(sentenceId = DPS_SENTENCE_ID)

        courtSentencingMappingApiMockServer.stubSentenceMappingCreateConflict(
          existingDpsSentenceId = EXISTING_DPS_SENTENCE_ID,
          duplicateDpsSentenceId = DPS_SENTENCE_ID,
          nomisSentenceSequence = NOMIS_SENTENCE_SEQUENCE,
        )

        awsSqsCourtSentencingOffenderEventsClient.sendMessage(
          courtSentencingQueueOffenderEventsUrl,
          sentenceEvent(
            eventType = "OFFENDER_SENTENCES-INSERTED",
            sentenceSequence = NOMIS_SENTENCE_SEQUENCE,
            bookingId = NOMIS_BOOKING_ID,
            offenderNo = OFFENDER_ID_DISPLAY,
          ),
        )

        // wait for mapping calls before verifying
        await untilAsserted {
          courtSentencingMappingApiMockServer.verify(
            1,
            postRequestedFor(urlPathEqualTo("/mapping/court-sentencing/sentences")),
          )
        }

        // doesn't retry
        dpsCourtSentencingServer.verify(
          1,
          postRequestedFor(urlPathEqualTo("/sentence")),
        )

        await untilAsserted {
          verify(telemetryClient).trackEvent(
            org.mockito.kotlin.eq("from-nomis-sync-sentence-duplicate"),
            check {
              assertThat(it["migrationId"]).isNull()
              assertThat(it["existingDpsSentenceId"]).isEqualTo(EXISTING_DPS_SENTENCE_ID)
              assertThat(it["duplicateDpsSentenceId"]).isEqualTo(DPS_SENTENCE_ID)
              assertThat(it["existingNomisSentenceSequence"]).isEqualTo(NOMIS_SENTENCE_SEQUENCE.toString())
              assertThat(it["duplicateNomisSentenceSequence"]).isEqualTo(NOMIS_SENTENCE_SEQUENCE.toString())
            },
            isNull(),
          )
        }
      }
    }
  }

  private fun waitForTelemetry(times: VerificationMode = atLeastOnce()) {
    await untilAsserted {
      verify(telemetryClient, times).trackEvent(
        any(),
        any(),
        isNull(),
      )
    }
  }
}

fun sentenceEvent(
  eventType: String,
  bookingId: Long = NOMIS_BOOKING_ID,
  sentenceSequence: Int = NOMIS_SENTENCE_SEQUENCE,
  offenderNo: String = OFFENDER_ID_DISPLAY,
  auditModule: String = "DPS",
) = """{
    "MessageId": "ae06c49e-1f41-4b9f-b2f2-dcca610d02cd", "Type": "Notification", "Timestamp": "2019-10-21T14:01:18.500Z", 
    "Message": "{\"eventId\":\"5958295\",\"eventType\":\"$eventType\",\"eventDatetime\":\"2019-10-21T15:00:25.489964\",\"bookingId\": \"$bookingId\",\"sentenceSequence\": \"$sentenceSequence\",\"offenderIdDisplay\": \"$offenderNo\",\"nomisEventType\":\"COURT_EVENT\",\"auditModuleName\":\"$auditModule\" }",
    "TopicArn": "arn:aws:sns:eu-west-1:000000000000:offender_events", 
    "MessageAttributes": {
      "eventType": {"Type": "String", "Value": "$eventType"}, 
      "id": {"Type": "String", "Value": "8b07cbd9-0820-0a0f-c32f-a9429b618e0b"}, 
      "contentType": {"Type": "String", "Value": "text/plain;charset=UTF-8"}, 
      "timestamp": {"Type": "Number.java.lang.Long", "Value": "1571666478344"}
    }
}
""".trimIndent()
