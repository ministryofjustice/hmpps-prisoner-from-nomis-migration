package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.advances

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.exactly
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
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
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.FinanceApiExtension.Companion.financeApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.FinanceIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath
import uk.gov.justice.hmpps.sqs.countAllMessagesOnQueue
import java.util.UUID

class AdvancesSynchronisationIntTest(
  @Autowired private val nomisApiMock: AdvancesNomisApiMockServer,
  @Autowired private val mappingApiMock: AdvancesMappingApiMockServer,
) : FinanceIntegrationTestBase() {

  @Nested
  @DisplayName("OFFENDER_ADVANCES-INSERTED")
  inner class OffenderAdvanceAdded {
    private val advanceId = 1234L
    private val prisonNumber = "A1234BC"

    @Nested
    inner class WhenCreatedInDps {
      @BeforeEach
      fun setUp() {
        sendAdvanceEvent("OFFENDER_ADVANCES-INSERTED", auditModuleName = "DPS_SYNCHRONISATION")
          .also { waitForAnyProcessingToComplete("prisoneradvance-synchronisation-created-skipped") }
      }

      @Test
      fun `will not attempt to retrieve the advance from NOMIS`() {
        nomisApiMock.verify(0, getRequestedFor(anyUrl()))
      }

      @Test
      fun `will not create the advance in DPS`() {
        financeApi.verify(0, postRequestedFor(urlPathEqualTo("/sync/advances")))
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("prisoneradvance-synchronisation-created-skipped"),
          check {
            assertThat(it["prisonNumber"]).isEqualTo(prisonNumber)
            assertThat(it["nomisAdvanceId"]).isEqualTo(advanceId.toString())
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenCreatedInNomis {
      @Nested
      inner class HappyPath {
        @BeforeEach
        fun setUp() {
          nomisApiMock.stubGetAdvance(advanceId)
          financeApi.stubSyncAdvance()
          sendAdvanceEvent("OFFENDER_ADVANCES-INSERTED")
            .also { waitForAnyProcessingToComplete("prisoneradvance-synchronisation-created-success") }
        }

        @Test
        fun `will retrieve the advance from NOMIS`() {
          nomisApiMock.verify(getRequestedFor(urlPathEqualTo("/finance/prisoners/advances/$advanceId")))
        }

        @Test
        fun `will create the advance in DPS`() {
          financeApi.verify(
            postRequestedFor(anyUrl())
              .withRequestBodyJsonPath("legacyPaymentProfileId", equalTo(advanceId.toString()))
              .withRequestBodyJsonPath("legacyInformationNumber", equalTo("info-123"))
              .withRequestBodyJsonPath("legacyTransactionId", equalTo("123"))
              .withRequestBodyJsonPath("prisonNumber", equalTo("A0001BC"))
              .withRequestBodyJsonPath("prisonID", equalTo("LEI"))
              .withRequestBodyJsonPath("amount", equalTo("2.1"))
              .withRequestBodyJsonPath("repaymentStartDate", equalTo("2024-06-18T00:00:00"))
              .withRequestBodyJsonPath("repaymentAmount", equalTo("0.5"))
              .withRequestBodyJsonPath("status", equalTo("ACTIVE"))
              .withRequestBodyJsonPath("comment", equalTo("This is a comment"))
              .withRequestBodyJsonPath("reference", equalTo("description of the advance"))
              .withRequestBodyJsonPath("createdBy", equalTo("JD12345"))
              .withRequestBodyJsonPath("createdOn", equalTo("2024-06-18T12:30:45")),
          )
        }

        @Test
        fun `will track telemetry`() {
          verify(telemetryClient).trackEvent(
            eq("prisoneradvance-synchronisation-created-success"),
            check {
              assertThat(it["prisonNumber"]).isEqualTo(prisonNumber)
              assertThat(it["nomisAdvanceId"]).isEqualTo(advanceId.toString())
            },
            isNull(),
          )
        }
      }

      @Nested
      @DisplayName("When mapping POST fails")
      inner class MappingFail {
        private val dpsAdvanceId = UUID.randomUUID()

        @BeforeEach
        fun setUp() {
          nomisApiMock.stubGetAdvance(advanceId)
          financeApi.stubSyncAdvance(id = dpsAdvanceId)
          mappingApiMock.stubGetAdvanceByNomisIdOrNull(nomisAdvanceId = advanceId, mapping = null)
        }

        @Nested
        @DisplayName("Fails once")
        inner class FailsOnce {
          @BeforeEach
          fun setUp() {
            mappingApiMock.stubCreateMappingFailureFollowedBySuccess()

            sendAdvanceEvent("OFFENDER_ADVANCES-INSERTED")
              .also { waitForAnyProcessingToComplete("prisoneradvance-synchronisation-mapping-created") }
          }

          @Test
          fun `will create the advance in DPS`() {
            financeApi.verify(
              postRequestedFor(urlPathEqualTo("/sync/advances")),
            )
          }

          @Test
          fun `will attempt to create mapping twice and succeed`() {
            mappingApiMock.verify(
              exactly(2),
              postRequestedFor(urlPathEqualTo("/mapping/advances"))
                .withRequestBodyJsonPath("dpsId", equalTo(dpsAdvanceId.toString()))
                .withRequestBodyJsonPath("nomisAdvanceId", equalTo(advanceId.toString()))
                .withRequestBodyJsonPath("mappingType", equalTo("NOMIS_CREATED")),
            )

            assertThat(
              awsSqsFinanceOffenderEventsDlqClient.countAllMessagesOnQueue(financeQueueOffenderEventsDlqUrl).get(),
            ).isEqualTo(0)
          }

          @Test
          fun `will track a telemetry event for partial success`() {
            verify(telemetryClient).trackEvent(
              eq("prisoneradvance-synchronisation-created-success"),
              check {
                assertThat(it["prisonNumber"]).isEqualTo(prisonNumber)
                assertThat(it["nomisAdvanceId"]).isEqualTo(advanceId.toString())
                assertThat(it["dpsAdvanceId"]).isEqualTo(dpsAdvanceId.toString())
                assertThat(it["mapping"]).isEqualTo("initial-failure")
              },
              isNull(),
            )

            verify(telemetryClient).trackEvent(
              eq("prisoneradvance-synchronisation-mapping-created"),
              check {
                assertThat(it["nomisAdvanceId"]).isEqualTo(advanceId.toString())
                assertThat(it["dpsAdvanceId"]).isEqualTo(dpsAdvanceId.toString())
              },
              isNull(),
            )
          }
        }

        @Nested
        @DisplayName("Fails constantly")
        inner class FailsConstantly {
          @BeforeEach
          fun setUp() {
            mappingApiMock.stubCreateMapping(status = INTERNAL_SERVER_ERROR)
            sendAdvanceEvent("OFFENDER_ADVANCES-INSERTED")
            await untilCallTo {
              awsSqsFinanceOffenderEventsDlqClient.countAllMessagesOnQueue(financeQueueOffenderEventsDlqUrl).get()
            } matches { it == 1 }
          }

          @Test
          fun `will create the advance in DPS`() {
            financeApi.verify(
              1,
              postRequestedFor(urlPathEqualTo("/sync/advances")),
            )
          }

          @Test
          fun `will attempt to create mapping several times and keep failing`() {
            mappingApiMock.verify(
              exactly(3),
              postRequestedFor(urlPathEqualTo("/mapping/advances")),
            )
          }

          @Test
          fun `will track a telemetry event for success`() {
            verify(telemetryClient).trackEvent(
              eq("prisoneradvance-synchronisation-created-success"),
              check {
                assertThat(it["prisonNumber"]).isEqualTo(prisonNumber)
                assertThat(it["nomisAdvanceId"]).isEqualTo(advanceId.toString())
                assertThat(it["dpsAdvanceId"]).isEqualTo(dpsAdvanceId.toString())
                assertThat(it["mapping"]).isEqualTo("initial-failure")
              },
              isNull(),
            )
          }
        }
      }

      @Nested
      @DisplayName("When finance api POST fails")
      inner class DPSApiFail {
        @BeforeEach
        fun setUp() {
          nomisApiMock.stubGetAdvance(advanceId)
          financeApi.stubSyncAdvanceFailure()

          sendAdvanceEvent("OFFENDER_ADVANCES-INSERTED")
        }

        @Test
        fun `will not attempt to create mapping and will track a telemetry event for failure`() {
          await untilAsserted {
            verify(telemetryClient, times(2)).trackEvent(
              eq("prisoneradvance-synchronisation-created-error"),
              check {
                assertThat(it["nomisAdvanceId"]).isEqualTo(advanceId.toString())
                assertThat(it["prisonNumber"]).isEqualTo(prisonNumber)
                assertThat(it["error"]).isEqualTo("500 Internal Server Error from POST http://localhost:8102/sync/advances")
              },
              isNull(),
            )
          }
          mappingApiMock.verify(
            0,
            postRequestedFor(urlPathEqualTo("/mapping/advances")),
          )
        }
      }
    }
  }

  private fun sendAdvanceEvent(
    eventType: String,
    advanceId: Long = 1234,
    auditModuleName: String = "NOMIS",
  ) = awsSqsFinanceOffenderEventsClient.sendMessage(
    financeQueueOffenderEventsUrl,
    offenderAdvancesEvent(eventType, advanceId, prisonNumber = "A1234BC", auditModuleName = auditModuleName),
  )
}

fun offenderAdvancesEvent(
  eventType: String,
  advanceId: Long,
  prisonNumber: String,
  auditModuleName: String = "OUUUSERS",
) = // language=JSON
  """{
    "MessageId": "ae06c49e-1f41-4b9f-b2f2-dcca610d02cd", "Type": "Notification", "Timestamp": "2019-10-21T14:01:18.500Z", 
    "Message": "{\"eventType\":\"$eventType\",\"eventDatetime\":\"2019-10-21T15:00:25.489964\",\"offenderAdvanceId\": $advanceId,\"offenderIdDisplay\":\"$prisonNumber\",\"auditModuleName\":\"$auditModuleName\",\"nomisEventType\":\"$eventType\" }",
    "TopicArn": "arn:aws:sns:eu-west-1:000000000000:offender_events", 
    "MessageAttributes": {
      "eventType": {"Type": "String", "Value": "$eventType"}, 
      "id": {"Type": "String", "Value": "8b07cbd9-0820-0a0f-c32f-a9429b618e0b"}, 
      "contentType": {"Type": "String", "Value": "text/plain;charset=UTF-8"}, 
      "timestamp": {"Type": "Number.java.lang.Long", "Value": "1571666478344"}
    }
}
  """.trimIndent()
