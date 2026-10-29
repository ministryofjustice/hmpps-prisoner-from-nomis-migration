package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.scheduledpayments

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.FinanceApiExtension.Companion.financeApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.FinanceIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorContent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ScheduledPaymentMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath
import java.time.LocalDate
import java.util.UUID

class ScheduledPaymentsSynchronisationIntTest(
  @Autowired private val nomisApiMock: ScheduledPaymentsNomisApiMockServer,
  @Autowired private val mappingApiMock: ScheduledPaymentsMappingApiMockServer,
) : FinanceIntegrationTestBase() {

  @Nested
  @DisplayName("OFFENDER_SCHEDULED_PAYMENTS-INSERTED")
  inner class ScheduledPaymentAdded {
    private val scheduledPaymentId = 12345L
    private val prisonNumber = "A1234BC"
    private val dpsScheduledPaymentId = UUID.fromString("de2bc56c-ea73-4f3c-8a37-5a46fdb2d79a")

    private fun verifyMappingSaved(count: Int = 1) {
      mappingApiMock.verify(
        count,
        postRequestedFor(urlPathEqualTo("/mapping/scheduled-payments"))
          .withRequestBodyJsonPath("nomisScheduledPaymentId", scheduledPaymentId)
          .withRequestBodyJsonPath("dpsId", dpsScheduledPaymentId.toString())
          .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED"),
      )
    }

    private fun verifySuccessTelemetry() {
      verify(telemetryClient).trackEvent(
        eq("scheduled-payments-synchronisation-created-success"),
        check {
          assertThat(it["prisonNumber"]).isEqualTo(prisonNumber)
          assertThat(it["nomisScheduledPaymentId"]).isEqualTo(scheduledPaymentId.toString())
          assertThat(it["dpsScheduledPaymentId"]).isEqualTo(dpsScheduledPaymentId.toString())
        },
        isNull(),
      )
    }

    @Nested
    inner class WhenCreatedInDps {
      @BeforeEach
      fun setUp() {
        sendScheduledPaymentEvent("OFFENDER_SCHEDULED_PAYMENTS-INSERTED", auditModuleName = "DPS_SYNCHRONISATION")
          .also { waitForAnyProcessingToComplete("scheduled-payments-synchronisation-created-skipped") }
      }

      @Test
      fun `will not attempt to retrieve the scheduled payment from NOMIS`() {
        nomisApiMock.verify(0, getRequestedFor(anyUrl()))
      }

      @Test
      fun `will not create the scheduled payment in DPS`() {
        financeApi.verify(0, postRequestedFor(urlPathEqualTo("/sync/subscriptions")))
      }

      @Test
      fun `will not call the mapping service`() {
        mappingApiMock.verify(0, getRequestedFor(anyUrl()))
        mappingApiMock.verify(0, postRequestedFor(anyUrl()))
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("scheduled-payments-synchronisation-created-skipped"),
          check {
            assertThat(it["prisonNumber"]).isEqualTo(prisonNumber)
            assertThat(it["nomisScheduledPaymentId"]).isEqualTo(scheduledPaymentId.toString())
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
          nomisApiMock.stubGetScheduledPayment(scheduledPaymentId, prisonNumber)
          mappingApiMock.stubGetScheduledPaymentByNomisIdOrNull(nomisScheduledPaymentId = scheduledPaymentId, mapping = null)
          financeApi.stubCreateScheduledPayment(subscriptionId = dpsScheduledPaymentId)
          mappingApiMock.stubCreateMapping()
        }

        @Nested
        inner class HappyPathNoFailures {
          @BeforeEach
          fun setUp() {
            sendScheduledPaymentEvent("OFFENDER_SCHEDULED_PAYMENTS-INSERTED")
              .also { waitForAnyProcessingToComplete("scheduled-payments-synchronisation-created-success") }
          }

          @Test
          fun `will check for an existing mapping`() {
            mappingApiMock.verify(getRequestedFor(urlPathEqualTo("/mapping/scheduled-payments/nomis-id/$scheduledPaymentId")))
          }

          @Test
          fun `will retrieve the scheduled payment from NOMIS`() {
            nomisApiMock.verify(getRequestedFor(urlPathEqualTo("/finance/scheduled-payments/$scheduledPaymentId")))
          }

          @Test
          fun `will create the scheduled payment in DPS`() {
            financeApi.verify(
              postRequestedFor(urlPathEqualTo("/sync/subscriptions"))
                .withRequestBodyJsonPath("legacyPaymentProfileId", equalTo(scheduledPaymentId.toString()))
                .withRequestBodyJsonPath("prisonNumber", equalTo(prisonNumber))
                .withRequestBodyJsonPath("prisonId", equalTo("LEI"))
                .withRequestBodyJsonPath("amount", equalTo("2.1"))
                .withRequestBodyJsonPath("startDate", equalTo("2024-06-18"))
                .withRequestBodyJsonPath("endDate", equalTo("2024-07-30"))
                .withRequestBodyJsonPath("subscriptionType", equalTo("TELE"))
                .withRequestBodyJsonPath("reference", equalTo("description of the scheduled payment"))
                .withRequestBodyJsonPath("createdBy", equalTo("JD12345")),
            )
          }

          @Test
          fun `will create the mapping`() {
            verifyMappingSaved()
          }

          @Test
          fun `will track telemetry`() {
            verifySuccessTelemetry()
          }
        }

        @Nested
        inner class HappyPathWithMappingFailures {
          @Nested
          inner class FailureAndRecovery {
            @BeforeEach
            fun setUp() {
              mappingApiMock.stubCreateMappingFailureFollowedBySuccess()
              sendScheduledPaymentEvent("OFFENDER_SCHEDULED_PAYMENTS-INSERTED")
                .also { waitForAnyProcessingToComplete("scheduled-payments-synchronisation-mapping-created") }
            }

            @Test
            fun `will eventually create mapping after a retry`() {
              verifyMappingSaved(2)
            }

            @Test
            fun `will create the scheduled payment in DPS only once`() {
              financeApi.verify(1, postRequestedFor(urlPathEqualTo("/sync/subscriptions")))
              nomisApiMock.verify(1, getRequestedFor(urlPathEqualTo("/finance/scheduled-payments/$scheduledPaymentId")))
            }

            @Test
            fun `will track telemetry`() {
              verifySuccessTelemetry()
              verify(telemetryClient).trackEvent(
                eq("scheduled-payments-synchronisation-mapping-created"),
                check {
                  assertThat(it["prisonNumber"]).isEqualTo(prisonNumber)
                  assertThat(it["nomisScheduledPaymentId"]).isEqualTo(scheduledPaymentId.toString())
                  assertThat(it["dpsScheduledPaymentId"]).isEqualTo(dpsScheduledPaymentId.toString())
                },
                isNull(),
              )
            }
          }

          @Nested
          inner class DuplicateDetected {
            private val existingDpsId = "existing-scheduled-payments-id"

            @BeforeEach
            fun setUp() {
              mappingApiMock.stubCreateMapping(
                error = DuplicateErrorResponse(
                  moreInfo = DuplicateErrorContent(
                    duplicate = ScheduledPaymentMappingDto(
                      dpsId = dpsScheduledPaymentId.toString(),
                      nomisScheduledPaymentId = scheduledPaymentId,
                      mappingType = ScheduledPaymentMappingDto.MappingType.NOMIS_CREATED,
                    ),
                    existing = ScheduledPaymentMappingDto(
                      dpsId = existingDpsId,
                      nomisScheduledPaymentId = scheduledPaymentId,
                      mappingType = ScheduledPaymentMappingDto.MappingType.NOMIS_CREATED,
                    ),
                  ),
                ),
              )
              sendScheduledPaymentEvent("OFFENDER_SCHEDULED_PAYMENTS-INSERTED")
                .also { waitForAnyProcessingToComplete("scheduled-payments-synchronisation-created-success") }
            }

            @Test
            fun `will create the scheduled payment in DPS only once`() {
              financeApi.verify(1, postRequestedFor(urlPathEqualTo("/sync/subscriptions")))
            }

            @Test
            fun `will not bother retrying mapping`() {
              verifyMappingSaved(1)
            }

            @Test
            fun `will track telemetry`() {
              verifySuccessTelemetry()
              verify(telemetryClient).trackEvent(
                eq("scheduled-payments-synchronisation-duplicate"),
                check {
                  assertThat(it["existingNomisScheduledPaymentId"]).isEqualTo(scheduledPaymentId.toString())
                  assertThat(it["existingDpsScheduledPaymentId"]).isEqualTo(existingDpsId)
                  assertThat(it["duplicateNomisScheduledPaymentId"]).isEqualTo(scheduledPaymentId.toString())
                  assertThat(it["duplicateDpsScheduledPaymentId"]).isEqualTo(dpsScheduledPaymentId.toString())
                },
                isNull(),
              )
            }
          }
        }
      }

      @Nested
      inner class UnHappyPath {
        @Nested
        inner class AlreadyExists {
          @BeforeEach
          fun setUp() {
            mappingApiMock.stubGetScheduledPaymentByNomisIdOrNull(
              nomisScheduledPaymentId = scheduledPaymentId,
              dpsId = dpsScheduledPaymentId.toString(),
            )
            sendScheduledPaymentEvent("OFFENDER_SCHEDULED_PAYMENTS-INSERTED")
              .also { waitForAnyProcessingToComplete("scheduled-payments-synchronisation-created-ignored") }
          }

          @Test
          fun `will not call NOMIS`() {
            nomisApiMock.verify(0, getRequestedFor(anyUrl()))
          }

          @Test
          fun `will not create the scheduled payment in DPS`() {
            financeApi.verify(0, postRequestedFor(urlPathEqualTo("/sync/subscriptions")))
          }

          @Test
          fun `will not attempt to create a mapping`() {
            mappingApiMock.verify(0, postRequestedFor(anyUrl()))
          }

          @Test
          fun `will track telemetry`() {
            verify(telemetryClient).trackEvent(
              eq("scheduled-payments-synchronisation-created-ignored"),
              check {
                assertThat(it["prisonNumber"]).isEqualTo(prisonNumber)
                assertThat(it["nomisScheduledPaymentId"]).isEqualTo(scheduledPaymentId.toString())
                assertThat(it["dpsScheduledPaymentId"]).isEqualTo(dpsScheduledPaymentId.toString())
              },
              isNull(),
            )
          }
        }
      }
    }
  }

  @Nested
  @DisplayName("OFFENDER_SCHEDULED_PAYMENTS-UPDATED")
  inner class ScheduledPaymentUpdated {
    private val scheduledPaymentId = 12345L
    private val prisonNumber = "A1234BC"
    private val dpsScheduledPaymentId = UUID.randomUUID()

    @Nested
    inner class WhenUpdatedInDps {
      @BeforeEach
      fun setUp() {
        sendScheduledPaymentEvent("OFFENDER_SCHEDULED_PAYMENTS-UPDATED", auditModuleName = "DPS_SYNCHRONISATION")
          .also { waitForAnyProcessingToComplete("scheduled-payments-synchronisation-updated-skipped") }
      }

      @Test
      fun `will not call NOMIS, the mapping service or DPS`() {
        nomisApiMock.verify(0, getRequestedFor(anyUrl()))
        mappingApiMock.verify(0, getRequestedFor(anyUrl()))
        financeApi.verify(0, postRequestedFor(anyUrl()))
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("scheduled-payments-synchronisation-updated-skipped"),
          check {
            assertThat(it["prisonNumber"]).isEqualTo(prisonNumber)
            assertThat(it["nomisScheduledPaymentId"]).isEqualTo(scheduledPaymentId.toString())
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenUpdatedInNomis {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisId(
          scheduledPaymentId,
          ScheduledPaymentMappingDto(
            nomisScheduledPaymentId = scheduledPaymentId,
            dpsId = dpsScheduledPaymentId.toString(),
            mappingType = ScheduledPaymentMappingDto.MappingType.NOMIS_CREATED,
          ),
        )
        financeApi.stubUpdateScheduledPayment(dpsScheduledPaymentId)
      }

      @Nested
      inner class WithEndDate {
        @BeforeEach
        fun setUp() {
          nomisApiMock.stubGetScheduledPayment(
            scheduledPaymentId,
            prisonNumber,
            prisonerScheduledPayment(prisonNumber).copy(endDate = LocalDate.parse("2024-08-06")),
          )
          sendScheduledPaymentEvent("OFFENDER_SCHEDULED_PAYMENTS-UPDATED")
            .also { waitForAnyProcessingToComplete("scheduled-payments-synchronisation-updated-success") }
        }

        @Test
        fun `will retrieve the scheduled payment from NOMIS`() {
          nomisApiMock.verify(getRequestedFor(urlPathEqualTo("/finance/scheduled-payments/$scheduledPaymentId")))
        }

        @Test
        fun `will update the end date in DPS`() {
          financeApi.verify(
            postRequestedFor(urlPathEqualTo("/sync/subscriptions/$dpsScheduledPaymentId/update"))
              .withRequestBodyJsonPath("endDate", equalTo("2024-08-06")),
          )
        }

        @Test
        fun `will track telemetry`() {
          verify(telemetryClient).trackEvent(
            eq("scheduled-payments-synchronisation-updated-success"),
            check {
              assertThat(it["prisonNumber"]).isEqualTo(prisonNumber)
              assertThat(it["nomisScheduledPaymentId"]).isEqualTo(scheduledPaymentId.toString())
              assertThat(it["dpsScheduledPaymentId"]).isEqualTo(dpsScheduledPaymentId.toString())
            },
            isNull(),
          )
        }
      }

      @Nested
      inner class WithoutEndDate {
        @BeforeEach
        fun setUp() {
          nomisApiMock.stubGetScheduledPayment(
            scheduledPaymentId,
            prisonNumber,
            prisonerScheduledPayment(prisonNumber).copy(endDate = null),
          )
          sendScheduledPaymentEvent("OFFENDER_SCHEDULED_PAYMENTS-UPDATED")
            .also { waitForAnyProcessingToComplete("scheduled-payments-synchronisation-updated-success") }
        }

        @Test
        fun `will not update the scheduled payment in DPS`() {
          financeApi.verify(0, postRequestedFor(urlPathEqualTo("/sync/subscriptions/$dpsScheduledPaymentId/update")))
        }

        @Test
        fun `will track telemetry for the ignored update`() {
          verify(telemetryClient).trackEvent(
            eq("scheduled-payments-synchronisation-updated-ignored-no-end-date"),
            check {
              assertThat(it["prisonNumber"]).isEqualTo(prisonNumber)
              assertThat(it["nomisScheduledPaymentId"]).isEqualTo(scheduledPaymentId.toString())
              assertThat(it["dpsScheduledPaymentId"]).isEqualTo(dpsScheduledPaymentId.toString())
            },
            isNull(),
          )
        }
      }
    }
  }

  private fun sendScheduledPaymentEvent(
    eventType: String,
    scheduledPaymentId: Long = 12345,
    auditModuleName: String = "NOMIS",
  ) = awsSqsFinanceOffenderEventsClient.sendMessage(
    financeQueueOffenderEventsUrl,
    scheduledPaymentEvent(eventType, scheduledPaymentId, auditModuleName = auditModuleName),
  )
}

fun scheduledPaymentEvent(
  eventType: String,
  scheduledPaymentId: Long,
  prisonNumber: String = "A1234BC",
  auditModuleName: String = "SCHEDPAY",
) = // language=JSON
  """{
    "MessageId": "ae06c49e-1f41-4b9f-b2f2-dcca610d02cd", "Type": "Notification", "Timestamp": "2019-10-21T14:01:18.500Z", 
    "Message": "{\"eventType\":\"$eventType\",\"eventDatetime\":\"2019-10-21T15:00:25.489964\",\"offenderScheduledPaymentId\": $scheduledPaymentId,\"offenderIdDisplay\":\"$prisonNumber\",\"auditModuleName\":\"$auditModuleName\",\"nomisEventType\":\"$eventType\" }",
    "TopicArn": "arn:aws:sns:eu-west-1:000000000000:offender_events", 
    "MessageAttributes": {
      "eventType": {"Type": "String", "Value": "$eventType"}, 
      "id": {"Type": "String", "Value": "8b07cbd9-0820-0a0f-c32f-a9429b618e0b"}, 
      "contentType": {"Type": "String", "Value": "text/plain;charset=UTF-8"}, 
      "timestamp": {"Type": "Number.java.lang.Long", "Value": "1571666478344"}
    }
}
  """.trimIndent()
