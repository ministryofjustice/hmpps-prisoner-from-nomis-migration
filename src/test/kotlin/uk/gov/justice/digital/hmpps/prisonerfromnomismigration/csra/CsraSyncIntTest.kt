package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csra

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyMap
import org.mockito.ArgumentMatchers.matches
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csra.CsraApiExtension.Companion.csraApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CsraMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath
import uk.gov.justice.hmpps.sqs.HmppsQueue

private const val DPS_ID = "e52d7268-6e10-41a8-a0b9-2319b32520d6"
private const val BOOKING_ID = 123456L
private const val SEQUENCE = 1
private const val OFFENDER_ID_DISPLAY = "A1234KT"

class CsraSyncIntTest(
  @Autowired private val csraNomisApiMockServer: CsraNomisApiMockServer,
  @Autowired private val csraMappingApiMockServer: CsraMappingApiMockServer,
) : CsraIntegrationTestBase() {
  override fun getQueues(): List<HmppsQueue> = listOf(csraEventQueue)

  @Nested
  @DisplayName("ASSESSMENT-INSERTED")
  inner class Created {
    @Nested
    @DisplayName("When CSRA was created in DPS")
    inner class DPSCreated {
      @BeforeEach
      fun setUp() = runTest {
        awsSqsCsraEventClient.sendMessage(
          csraEventQueueUrl,
          csraEvent(
            eventType = "ASSESSMENT-INSERTED",
            bookingId = BOOKING_ID,
            assessmentSeq = SEQUENCE,
            offenderNo = OFFENDER_ID_DISPLAY,
            auditModuleName = "DPS_SYNCHRONISATION",
          ),
        )
      }

      @Test
      fun `the event is ignored`() {
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("csras-synchronisation-created-skipped"),
            check {
              assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
              assertThat(it["bookingId"]).isEqualTo(BOOKING_ID.toString())
              assertThat(it["sequence"]).isEqualTo(SEQUENCE.toString())
            },
            isNull(),
          )
        }

        // will not bother getting mapping
        csraMappingApiMockServer.verify(0, getRequestedFor(anyUrl()))
        // will not call DPS sync
        csraApi.verify(0, postRequestedFor(anyUrl()))
      }
    }

    @Nested
    @DisplayName("When CSRA was created in NOMIS")
    inner class NomisCreated {
      @Nested
      @DisplayName("Happy path")
      inner class HappyPath {
        @BeforeEach
        fun setUp() {
          csraNomisApiMockServer.stubGetCsra(bookingId = BOOKING_ID, sequence = SEQUENCE)
          csraApi.stubSyncCsraCreate(OFFENDER_ID_DISPLAY, DPS_ID)
          csraMappingApiMockServer.stubPostMapping()

          awsSqsCsraEventClient.sendMessage(
            csraEventQueueUrl,
            csraEvent(
              eventType = "ASSESSMENT-INSERTED",
              bookingId = BOOKING_ID,
              assessmentSeq = SEQUENCE,
              offenderNo = OFFENDER_ID_DISPLAY,
            ),
          )
          waitForCompletion()
        }

        @Test
        fun `will POST CSRA to DPS`() {
          csraApi.verify(
            1,
            postRequestedFor(anyUrl())
              .withRequestBodyJsonPath("review.bookingId", equalTo(BOOKING_ID.toString()))
              .withRequestBodyJsonPath("review.nomisSequence", equalTo(SEQUENCE.toString()))
              .withRequestBodyJsonPath("review.assessmentDate", "2021-02-03")
              .withRequestBodyJsonPath("review.assessmentPrisonId", "SWI")
              .withRequestBodyJsonPath("review.assessmentType", "CSR")
              .withRequestBodyJsonPath("review.calculatedLevel", "STANDARD")
              .withRequestBodyJsonPath("review.score", "1001")
              .withRequestBodyJsonPath("review.status", "A")
              .withRequestBodyJsonPath("review.committeeCode", "GOV")
              .withRequestBodyJsonPath("review.nextReviewDate", "2021-02-03")
              .withRequestBodyJsonPath("review.comment", "comment")
              .withRequestBodyJsonPath("review.placementPrisonId", "placementAgencyId")
              .withRequestBodyJsonPath("review.createdDateTime", "2024-11-03T04:05:06")
              .withRequestBodyJsonPath("review.createdBy", "me")
              .withRequestBodyJsonPath("review.reviewLevel", "LOW")
              .withRequestBodyJsonPath("review.approvedLevel", "MED")
              .withRequestBodyJsonPath("review.evaluationDate", "2021-02-03")
              .withRequestBodyJsonPath("review.evaluationResultCode", "APP")
              .withRequestBodyJsonPath("review.reviewCommitteeCode", "SECSTATE")
              .withRequestBodyJsonPath("review.reviewCommitteeComment", "reviewCommitteeComment")
              .withRequestBodyJsonPath("review.reviewPlacementPrisonId", "reviewPlacementAgencyId")
              .withRequestBodyJsonPath("review.reviewComment", "reviewComment")
              .withRequestBodyJsonPath("review.reviewDetails[0].code", "CODE1")
              .withRequestBodyJsonPath("review.reviewDetails[0].description", "section description")
              .withRequestBodyJsonPath("review.reviewDetails[0].questions[0].code", "CODE2")
              .withRequestBodyJsonPath("review.reviewDetails[0].questions[0].description", "question description")
              .withRequestBodyJsonPath("review.reviewDetails[0].questions[0].responses[0].code", "CODE3")
              .withRequestBodyJsonPath("review.reviewDetails[0].questions[0].responses[0].answer", "answer")
              .withRequestBodyJsonPath(
                "review.reviewDetails[0].questions[0].responses[0].comment",
                "response comment",
              ),
          )
        }

        @Test
        fun `will create mapping between DPS and NOMIS ids`() {
          csraMappingApiMockServer.verify(
            postRequestedFor(urlPathEqualTo("/mapping/csras"))
              .withRequestBodyJsonPath("dpsCsraId", equalTo(DPS_ID))
              .withRequestBodyJsonPath("nomisBookingId", equalTo(BOOKING_ID.toString()))
              .withRequestBodyJsonPath("nomisSequence", equalTo(SEQUENCE.toString()))
              .withRequestBodyJsonPath("offenderNo", equalTo(OFFENDER_ID_DISPLAY)),
          )
        }

        @Test
        fun `will track a telemetry event for success`() {
          verify(telemetryClient).trackEvent(
            eq("csras-synchronisation-created-success"),
            check {
              assertThat(it["dpsCsraId"]).isEqualTo(DPS_ID)
              assertThat(it["bookingId"]).isEqualTo(BOOKING_ID.toString())
              assertThat(it["sequence"]).isEqualTo(SEQUENCE.toString())
              assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
            },
            isNull(),
          )
        }
      }

      @Nested
      @DisplayName("Error scenarios")
      inner class Exceptions {
        @Test
        fun `Nomis call fails`() {
          csraNomisApiMockServer.stubGetCsraError(bookingId = BOOKING_ID, sequence = SEQUENCE)

          awsSqsCsraEventClient.sendMessage(
            csraEventQueueUrl,
            csraEvent(
              eventType = "ASSESSMENT-INSERTED",
              bookingId = BOOKING_ID,
              assessmentSeq = SEQUENCE,
              offenderNo = OFFENDER_ID_DISPLAY,
            ),
          )

          await untilAsserted {
            verify(telemetryClient, times(2)).trackEvent(
              eq("csras-synchronisation-created-failed"),
              check {
                assertThat(it["bookingId"]).isEqualTo(BOOKING_ID.toString())
                assertThat(it["sequence"]).isEqualTo(SEQUENCE.toString())
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
                assertThat(it["error"]).isEqualTo("500 Internal Server Error from GET http://localhost:8081/prisoners/booking-id/$BOOKING_ID/csra/$SEQUENCE")
              },
              isNull(),
            )
          }
        }

        @Test
        fun `DPS call fails`() {
          csraNomisApiMockServer.stubGetCsra(bookingId = BOOKING_ID, sequence = SEQUENCE)
          csraApi.stubSyncCsraCreateError(OFFENDER_ID_DISPLAY)

          awsSqsCsraEventClient.sendMessage(
            csraEventQueueUrl,
            csraEvent(
              eventType = "ASSESSMENT-INSERTED",
              bookingId = BOOKING_ID,
              assessmentSeq = SEQUENCE,
              offenderNo = OFFENDER_ID_DISPLAY,
            ),
          )

          await untilAsserted {
            verify(telemetryClient, times(2)).trackEvent(
              eq("csras-synchronisation-created-failed"),
              check {
                assertThat(it["bookingId"]).isEqualTo(BOOKING_ID.toString())
                assertThat(it["sequence"]).isEqualTo(SEQUENCE.toString())
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
                assertThat(it["error"]).isEqualTo("500 Internal Server Error from POST http://localhost:8105/nomis-sync/sync/$OFFENDER_ID_DISPLAY")
              },
              isNull(),
            )
          }
        }

        @Test
        fun `Mapping fails temporarily`() {
          csraNomisApiMockServer.stubGetCsra(bookingId = BOOKING_ID, sequence = SEQUENCE)
          csraApi.stubSyncCsraCreate(OFFENDER_ID_DISPLAY, DPS_ID)
          csraMappingApiMockServer.stubPostMappingFailureFollowedBySuccess()

          awsSqsCsraEventClient.sendMessage(
            csraEventQueueUrl,
            csraEvent(
              eventType = "ASSESSMENT-INSERTED",
              bookingId = BOOKING_ID,
              assessmentSeq = SEQUENCE,
              offenderNo = OFFENDER_ID_DISPLAY,
            ),
          )

          await untilAsserted {
            verify(telemetryClient, times(1)).trackEvent(
              eq("csras-synchronisation-created-success"),
              check {
                assertThat(it["bookingId"]).isEqualTo(BOOKING_ID.toString())
                assertThat(it["sequence"]).isEqualTo(SEQUENCE.toString())
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
                assertThat(it["dpsCsraId"]).isEqualTo(DPS_ID)
                assertThat(it["mapping"]).isEqualTo("initial-failure")
              },
              isNull(),
            )
            verify(telemetryClient, times(1)).trackEvent(
              eq("csras-mapping-created-success"),
              check {
                assertThat(it["bookingId"]).isEqualTo(BOOKING_ID.toString())
                assertThat(it["sequence"]).isEqualTo(SEQUENCE.toString())
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
                assertThat(it["dpsCsraId"]).isEqualTo(DPS_ID)
                assertThat(it["original-error"]).isEqualTo("500 Internal Server Error from POST http://localhost:8083/mapping/csras")
              },
              isNull(),
            )
          }
        }

        @Test
        fun `Mapping fails permanently`() {
          csraNomisApiMockServer.stubGetCsra(bookingId = BOOKING_ID, sequence = SEQUENCE)
          csraApi.stubSyncCsraCreate(OFFENDER_ID_DISPLAY, DPS_ID)
          csraMappingApiMockServer.stubPostMapping(HttpStatus.INTERNAL_SERVER_ERROR)

          awsSqsCsraEventClient.sendMessage(
            csraEventQueueUrl,
            csraEvent(
              eventType = "ASSESSMENT-INSERTED",
              bookingId = BOOKING_ID,
              assessmentSeq = SEQUENCE,
              offenderNo = OFFENDER_ID_DISPLAY,
            ),
          )

          await untilAsserted {
            verify(telemetryClient, times(1)).trackEvent(
              eq("csras-synchronisation-created-success"),
              check {
                assertThat(it["bookingId"]).isEqualTo(BOOKING_ID.toString())
                assertThat(it["sequence"]).isEqualTo(SEQUENCE.toString())
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
                assertThat(it["dpsCsraId"]).isEqualTo(DPS_ID)
                assertThat(it["mapping"]).isEqualTo("initial-failure")
              },
              isNull(),
            )
            verify(telemetryClient, times(2)).trackEvent(
              eq("csras-mapping-created-failure"),
              check {
                assertThat(it["bookingId"]).isEqualTo(BOOKING_ID.toString())
                assertThat(it["sequence"]).isEqualTo(SEQUENCE.toString())
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
                assertThat(it["dpsCsraId"]).isEqualTo(DPS_ID)
                assertThat(it["error"]).isEqualTo("500 Internal Server Error from POST http://localhost:8083/mapping/csras")
              },
              isNull(),
            )
          }
        }
      }
    }
  }

  @Nested
  @DisplayName("ASSESSMENT-UPDATED")
  inner class Updated {
    @Nested
    @DisplayName("When property was updated in DPS")
    inner class DPSUpdated {

      @BeforeEach
      fun setUp() = runTest {
        awsSqsCsraEventClient.sendMessage(
          csraEventQueueUrl,
          csraEvent(
            eventType = "ASSESSMENT-UPDATED",
            bookingId = BOOKING_ID,
            assessmentSeq = SEQUENCE,
            offenderNo = OFFENDER_ID_DISPLAY,
            auditModuleName = "DPS_SYNCHRONISATION",
          ),
        )
      }

      @Test
      fun `the event is ignored`() {
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("csras-synchronisation-updated-skipped"),
            check {
              assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
              assertThat(it["bookingId"]).isEqualTo(BOOKING_ID.toString())
              assertThat(it["sequence"]).isEqualTo(SEQUENCE.toString())
            },
            isNull(),
          )
        }

        // will not bother getting mapping
        csraMappingApiMockServer.verify(0, getRequestedFor(anyUrl()))
        // will not call DPS sync
        csraApi.verify(0, postRequestedFor(anyUrl()))
      }
    }

    @Nested
    @DisplayName("When CSRA was created in NOMIS")
    inner class NomisCreated {
      @Nested
      inner class HappyPath {
        @BeforeEach
        fun setUp() {
          csraMappingApiMockServer.stubGetByNomisId(
            bookingId = BOOKING_ID,
            sequence = SEQUENCE,
            CsraMappingDto(
              dpsCsraId = DPS_ID,
              nomisBookingId = BOOKING_ID,
              nomisSequence = SEQUENCE,
              offenderNo = OFFENDER_ID_DISPLAY,
              mappingType = CsraMappingDto.MappingType.NOMIS_CREATED,
            ),
          )
          csraNomisApiMockServer.stubGetCsra(bookingId = BOOKING_ID, sequence = SEQUENCE)
          csraApi.stubSyncCsraUpdate(OFFENDER_ID_DISPLAY, DPS_ID)

          awsSqsCsraEventClient.sendMessage(
            csraEventQueueUrl,
            csraEvent(
              eventType = "ASSESSMENT-UPDATED",
              bookingId = BOOKING_ID,
              assessmentSeq = SEQUENCE,
              offenderNo = OFFENDER_ID_DISPLAY,
            ),
          )
          waitForCompletion()
        }

        @Test
        fun `will POST CSRA to DPS`() {
          csraApi.verify(
            1,
            postRequestedFor(anyUrl())
              .withRequestBodyJsonPath("csraReviewId", equalTo(DPS_ID))
              .withRequestBodyJsonPath("review.bookingId", equalTo(BOOKING_ID.toString()))
              .withRequestBodyJsonPath("review.nomisSequence", equalTo(SEQUENCE.toString()))
              .withRequestBodyJsonPath("review.assessmentDate", "2021-02-03")
              .withRequestBodyJsonPath("review.assessmentPrisonId", "SWI")
              .withRequestBodyJsonPath("review.assessmentType", "CSR")
              .withRequestBodyJsonPath("review.calculatedLevel", "STANDARD")
              .withRequestBodyJsonPath("review.score", "1001")
              .withRequestBodyJsonPath("review.status", "A")
              .withRequestBodyJsonPath("review.committeeCode", "GOV")
              .withRequestBodyJsonPath("review.nextReviewDate", "2021-02-03")
              .withRequestBodyJsonPath("review.comment", "comment")
              .withRequestBodyJsonPath("review.placementPrisonId", "placementAgencyId")
              .withRequestBodyJsonPath("review.createdDateTime", "2024-11-03T04:05:06")
              .withRequestBodyJsonPath("review.createdBy", "me")
              .withRequestBodyJsonPath("review.reviewLevel", "LOW")
              .withRequestBodyJsonPath("review.approvedLevel", "MED")
              .withRequestBodyJsonPath("review.evaluationDate", "2021-02-03")
              .withRequestBodyJsonPath("review.evaluationResultCode", "APP")
              .withRequestBodyJsonPath("review.reviewCommitteeCode", "SECSTATE")
              .withRequestBodyJsonPath("review.reviewCommitteeComment", "reviewCommitteeComment")
              .withRequestBodyJsonPath("review.reviewPlacementPrisonId", "reviewPlacementAgencyId")
              .withRequestBodyJsonPath("review.reviewComment", "reviewComment")
              .withRequestBodyJsonPath("review.reviewDetails[0].code", "CODE1")
              .withRequestBodyJsonPath("review.reviewDetails[0].description", "section description")
              .withRequestBodyJsonPath("review.reviewDetails[0].questions[0].code", "CODE2")
              .withRequestBodyJsonPath("review.reviewDetails[0].questions[0].description", "question description")
              .withRequestBodyJsonPath("review.reviewDetails[0].questions[0].responses[0].code", "CODE3")
              .withRequestBodyJsonPath("review.reviewDetails[0].questions[0].responses[0].answer", "answer")
              .withRequestBodyJsonPath(
                "review.reviewDetails[0].questions[0].responses[0].comment",
                "response comment",
              ),
          )
        }

        @Test
        fun `will track a telemetry event for success`() {
          verify(telemetryClient).trackEvent(
            eq("csras-synchronisation-updated-success"),
            check {
              assertThat(it["dpsCsraId"]).isEqualTo(DPS_ID)
              assertThat(it["bookingId"]).isEqualTo(BOOKING_ID.toString())
              assertThat(it["sequence"]).isEqualTo(SEQUENCE.toString())
              assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
            },
            isNull(),
          )
        }
      }
    }
  }

  private fun waitForCompletion() {
    await untilAsserted {
      verify(telemetryClient).trackEvent(matches("csras-synchronisation-(cre|upd)ated-success"), anyMap(), isNull())
    }
  }
}

fun csraEvent(
  eventType: String,
  bookingId: Long = BOOKING_ID,
  assessmentSeq: Int = SEQUENCE,
  offenderNo: String = OFFENDER_ID_DISPLAY,
  auditModuleName: String? = "OIDSTUFF",
) = """{
  "MessageId": "ae06c49e-1f41-4b9f-b2f2-dcca610d02cd", "Type": "Notification", "Timestamp": "2019-10-21T14:01:18.500Z",
  "Message": "{\"eventType\":\"$eventType\",\"assessmentType\":\"CSR\",\"eventDatetime\":\"2024-07-10T15:00:25.489964\",\"bookingId\": \"$bookingId\",\"offenderIdDisplay\": \"$offenderNo\",\"nomisEventType\":\"$eventType\",\"assessmentSeq\": $assessmentSeq,\"auditModuleName\":\"$auditModuleName\" }",
  "TopicArn": "arn:aws:sns:eu-west-1:000000000000:offender_events",
  "MessageAttributes": {
    "eventType": {"Type": "String", "Value": "$eventType"},
    "id": {"Type": "String", "Value": "8b07cbd9-0820-0a0f-c32f-a9429b618e0b"},
    "contentType": {"Type": "String", "Value": "text/plain;charset=UTF-8"},
    "timestamp": {"Type": "Number.java.lang.Long", "Value": "1571666478344"}
  }
}
""".trimIndent()
