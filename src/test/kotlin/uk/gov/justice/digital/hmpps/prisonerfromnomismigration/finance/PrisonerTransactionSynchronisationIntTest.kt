package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.exactly
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilAsserted
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.eq
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.check
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.http.HttpStatus.NOT_FOUND
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.FinanceApiExtension.Companion.financeApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.model.SyncTransactionReceipt
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.EventType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MessageAttributes
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.SQSMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TransactionMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.TransactionIdBufferRepository
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath
import uk.gov.justice.hmpps.sqs.countAllMessagesOnQueue
import java.util.AbstractMap.SimpleEntry
import java.util.UUID

class PrisonerTransactionSynchronisationIntTest : SqsIntegrationTestBase() {

  companion object {
    val dpsTransactionUuid: UUID = UUID.fromString(DPS_TRANSACTION_ID)
    val messageUuid: UUID = UUID.fromString(MESSAGE_ID)
    internal const val BOOKING_ID = 1234L
    internal const val NOMIS_TRANSACTION_ID = 2345678L
    internal const val OFFENDER_ID = 101L
    internal const val OFFENDER_ID_DISPLAY = "A3864DZ"
    internal const val DPS_TRANSACTION_ID = "a04f7a8d-61aa-400c-9395-000011112222"
    internal const val MESSAGE_ID = "abcdef01-0000-1111-2222-000011112222"
  }

  @Autowired
  private lateinit var financeNomisApiMockServer: FinanceNomisApiMockServer

  @Autowired
  private lateinit var jsonMapper: JsonMapper

  @Autowired
  private lateinit var financeMappingApiMockServer: FinanceMappingApiMockServer

  @Autowired
  private lateinit var transactionIdBufferRepository: TransactionIdBufferRepository

  @Nested
  @DisplayName("OFFENDER_TRANSACTIONS-INSERTED")
  inner class PrisonerTransactionInserted {

    @Nested
    @DisplayName("When transaction was created in DPS")
    inner class DPSCreated {

      @BeforeEach
      fun setUp() {
        val message = offenderTransactionEvent(
          "OFFENDER_TRANSACTIONS-INSERTED",
          messageUuid,
          bookingId = BOOKING_ID,
          offenderNo = OFFENDER_ID_DISPLAY,
          auditModuleName = "DPS_SYNCHRONISATION",
        )
        financeOffenderEventsQueue.sendMessage(
          message,
        )
      }

      @Test
      fun `the event is ignored`() {
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("transactions-synchronisation-created-skipped"),
            check {
              assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
              assertThat(it["bookingId"]).isEqualTo(BOOKING_ID.toString())
              assertThat(it["nomisTransactionId"]).isEqualTo(NOMIS_TRANSACTION_ID.toString())
            },
            isNull(),
          )
        }

        // will not bother getting Nomis or mapping data
        financeNomisApiMockServer.verify(
          count = 0,
          getRequestedFor(urlPathMatching("/transactions/\\d+")),
        )
        financeMappingApiMockServer.verify(
          count = 0,
          getRequestedFor(urlPathMatching("/mapping/transactions/nomis-transaction-id/\\d+")),
        )
        // will not create a transaction in DPS
        financeApi.verify(0, postRequestedFor(anyUrl()))
      }
    }

    @Nested
    @DisplayName("When transaction was created in NOMIS")
    inner class NomisCreated {

      @BeforeEach
      fun setUp() = runTest {
        transactionIdBufferRepository.deleteAll()
      }

      @Nested
      @DisplayName("Happy path where transaction does not already exist in DPS")
      inner class HappyPathOffenderTransactionFirst {
        val receipt = SyncTransactionReceipt(
          synchronizedTransactionId = dpsTransactionUuid,
          requestId = UUID.randomUUID(),
          action = SyncTransactionReceipt.Action.CREATED,
        )

        val nomisTransactions = nomisTransactions()

        @BeforeEach
        fun setUp() {
          financeNomisApiMockServer.stubGetOffenderTransaction(
            bookingId = BOOKING_ID,
            transactionId = NOMIS_TRANSACTION_ID,
            response = nomisTransactions,
          )
          financeApi.stubPostOffenderTransaction(receipt)
          financeMappingApiMockServer.stubPostMapping()

          financeOffenderEventsQueue.sendMessage(
            offenderTransactionEvent(
              "OFFENDER_TRANSACTIONS-INSERTED",
              messageUuid,
              bookingId = BOOKING_ID,
              transactionId = NOMIS_TRANSACTION_ID,
              offenderNo = OFFENDER_ID_DISPLAY,
            ),
          )
        }

        @Test
        fun `will create transaction in DPS`() {
          await untilAsserted {
            val t1 = nomisTransactions.first()
            val g1 = t1.generalLedgerTransactions.first()
            financeApi.verify(
              1,
              postRequestedFor(urlPathEqualTo("/sync/offender-transactions"))
                .withRequestBodyJsonPath("transactionId", equalTo(NOMIS_TRANSACTION_ID.toString()))
                .withRequestBodyJsonPath("requestId", equalTo(MESSAGE_ID))
                .withRequestBodyJsonPath("caseloadId", equalTo("SWI"))
                .withRequestBodyJsonPath("transactionTimestamp", equalTo(g1.transactionTimestamp.toString()))
                .withRequestBodyJsonPath("createdAt", equalTo(t1.createdAt.toString()))
                .withRequestBodyJsonPath("createdBy", equalTo(t1.createdBy))
                .withRequestBodyJsonPath("createdByDisplayName", equalTo(t1.createdByDisplayName))
                .withRequestBodyJsonPath("lastModifiedAt", equalTo(t1.lastModifiedAt.toString()))
                .withRequestBodyJsonPath("lastModifiedBy", equalTo(t1.lastModifiedBy))
                .withRequestBodyJsonPath("lastModifiedByDisplayName", equalTo(t1.lastModifiedByDisplayName))
                .withRequestBodyJsonPath(
                  "offenderTransactions[0].entrySequence",
                  equalTo(t1.transactionEntrySequence.toString()),
                )
                .withRequestBodyJsonPath("offenderTransactions[0].offenderId", equalTo(OFFENDER_ID.toString()))
                .withRequestBodyJsonPath("offenderTransactions[0].offenderDisplayId", equalTo(OFFENDER_ID_DISPLAY))
                .withRequestBodyJsonPath("offenderTransactions[0].subAccountType", equalTo(t1.subAccountType.name))
                .withRequestBodyJsonPath("offenderTransactions[0].postingType", equalTo(t1.postingType.name))
                .withRequestBodyJsonPath("offenderTransactions[0].type", equalTo(t1.type))
                .withRequestBodyJsonPath("offenderTransactions[0].description", equalTo(t1.description))
                .withRequestBodyJsonPath("offenderTransactions[0].amount", equalTo("5.4"))
                .withRequestBodyJsonPath("offenderTransactions[0].offenderBookingId", equalTo(t1.bookingId.toString()))
                .withRequestBodyJsonPath("offenderTransactions[0].reference", equalTo(t1.reference))
                .withRequestBodyJsonPath(
                  "offenderTransactions[0].generalLedgerEntries[0].entrySequence",
                  equalTo(g1.generalLedgerEntrySequence.toString()),
                )
                .withRequestBodyJsonPath(
                  "offenderTransactions[0].generalLedgerEntries[0].code",
                  equalTo(g1.accountCode.toString()),
                )
                .withRequestBodyJsonPath(
                  "offenderTransactions[0].generalLedgerEntries[0].postingType",
                  equalTo(g1.postingType.name),
                )
                .withRequestBodyJsonPath("offenderTransactions[0].generalLedgerEntries[0].amount", equalTo("5.4")),
            )
          }
        }

        @Test
        fun `will create mapping between DPS and NOMIS ids`() {
          await untilAsserted {
            financeMappingApiMockServer.verify(
              postRequestedFor(urlPathEqualTo("/mapping/transactions"))
                .withRequestBody(matchingJsonPath("dpsTransactionId", equalTo(DPS_TRANSACTION_ID)))
                .withRequestBody(matchingJsonPath("nomisBookingId", equalTo(BOOKING_ID.toString())))
                .withRequestBody(matchingJsonPath("offenderNo", equalTo(OFFENDER_ID_DISPLAY)))
                .withRequestBody(matchingJsonPath("nomisTransactionId", equalTo(NOMIS_TRANSACTION_ID.toString())))
                .withRequestBody(matchingJsonPath("mappingType", equalTo("NOMIS_CREATED"))),
            )
          }
        }

        @Test
        fun `will track a telemetry event for success`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("transactions-synchronisation-created-success"),
              check {
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
                assertThat(it["bookingId"]).isEqualTo(BOOKING_ID.toString())
                assertThat(it["nomisTransactionId"]).isEqualTo(NOMIS_TRANSACTION_ID.toString())
                assertThat(it["dpsTransactionId"]).isEqualTo(DPS_TRANSACTION_ID)
                assertThat(it).doesNotContain(SimpleEntry("mapping", "initial-failure"))
              },
              isNull(),
            )
          }
        }
      }

      @Nested
      @DisplayName("Happy path where the transaction id already exists in DPS")
      inner class HappyPathOffenderTransactionNotFirst {
        val receipt = SyncTransactionReceipt(
          synchronizedTransactionId = dpsTransactionUuid,
          requestId = UUID.randomUUID(),
          action = SyncTransactionReceipt.Action.UPDATED,
        )

        val nomisTransactions = nomisTransactions()

        @BeforeEach
        fun setUp() {
          financeNomisApiMockServer.stubGetOffenderTransaction(
            bookingId = BOOKING_ID,
            transactionId = NOMIS_TRANSACTION_ID,
            response = nomisTransactions,
          )
          financeMappingApiMockServer.stubGetByNomisId(
            NOMIS_TRANSACTION_ID,
            TransactionMappingDto(
              nomisBookingId = BOOKING_ID,
              dpsTransactionId = DPS_TRANSACTION_ID,
              nomisTransactionId = NOMIS_TRANSACTION_ID,
              offenderNo = OFFENDER_ID_DISPLAY,
              mappingType = TransactionMappingDto.MappingType.NOMIS_CREATED,
            ),
          )
          financeApi.stubPostOffenderTransaction(receipt)
          financeMappingApiMockServer.stubPostMapping()

          financeOffenderEventsQueue.sendMessage(
            offenderTransactionEvent(
              "OFFENDER_TRANSACTIONS-INSERTED",
              messageUuid,
            ),
          )
        }

        @Test
        fun `will update transaction in DPS`() {
          await untilAsserted {
            val t1 = nomisTransactions.first()
            val g1 = t1.generalLedgerTransactions.first()
            financeApi.verify(
              1,
              postRequestedFor(urlPathEqualTo("/sync/offender-transactions"))
                .withRequestBodyJsonPath("transactionId", equalTo(NOMIS_TRANSACTION_ID.toString()))
                .withRequestBodyJsonPath("requestId", equalTo(MESSAGE_ID))
                .withRequestBodyJsonPath("caseloadId", equalTo("SWI"))
                .withRequestBodyJsonPath("transactionTimestamp", equalTo(g1.transactionTimestamp.toString()))
                .withRequestBodyJsonPath("createdAt", equalTo(t1.createdAt.toString()))
                .withRequestBodyJsonPath("createdBy", equalTo(t1.createdBy))
                .withRequestBodyJsonPath("createdByDisplayName", equalTo(t1.createdByDisplayName))
                .withRequestBodyJsonPath("lastModifiedAt", equalTo(t1.lastModifiedAt.toString()))
                .withRequestBodyJsonPath("lastModifiedBy", equalTo(t1.lastModifiedBy))
                .withRequestBodyJsonPath("lastModifiedByDisplayName", equalTo(t1.lastModifiedByDisplayName))
                .withRequestBodyJsonPath(
                  "offenderTransactions[0].entrySequence",
                  equalTo(t1.transactionEntrySequence.toString()),
                )
                .withRequestBodyJsonPath("offenderTransactions[0].offenderId", equalTo(OFFENDER_ID.toString()))
                .withRequestBodyJsonPath("offenderTransactions[0].offenderDisplayId", equalTo(OFFENDER_ID_DISPLAY))
                .withRequestBodyJsonPath("offenderTransactions[0].subAccountType", equalTo(t1.subAccountType.name))
                .withRequestBodyJsonPath("offenderTransactions[0].postingType", equalTo(t1.postingType.name))
                .withRequestBodyJsonPath("offenderTransactions[0].type", equalTo(t1.type))
                .withRequestBodyJsonPath("offenderTransactions[0].description", equalTo(t1.description))
                .withRequestBodyJsonPath("offenderTransactions[0].amount", equalTo("5.4"))
                .withRequestBodyJsonPath("offenderTransactions[0].offenderBookingId", equalTo(t1.bookingId.toString()))
                .withRequestBodyJsonPath("offenderTransactions[0].reference", equalTo(t1.reference))
                .withRequestBodyJsonPath(
                  "offenderTransactions[0].generalLedgerEntries[0].entrySequence",
                  equalTo(g1.generalLedgerEntrySequence.toString()),
                )
                .withRequestBodyJsonPath(
                  "offenderTransactions[0].generalLedgerEntries[0].code",
                  equalTo(g1.accountCode.toString()),
                )
                .withRequestBodyJsonPath(
                  "offenderTransactions[0].generalLedgerEntries[0].postingType",
                  equalTo(g1.postingType.name),
                )
                .withRequestBodyJsonPath("offenderTransactions[0].generalLedgerEntries[0].amount", equalTo("5.4")),
            )
          }
        }

        @Test
        fun `will track a telemetry event for success`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("transactions-synchronisation-created-success-additional"),
              check {
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
                assertThat(it["bookingId"]).isEqualTo(BOOKING_ID.toString())
                assertThat(it["nomisTransactionId"]).isEqualTo(NOMIS_TRANSACTION_ID.toString())
                assertThat(it["dpsTransactionId"]).isEqualTo(DPS_TRANSACTION_ID)
                assertThat(it).doesNotContain(SimpleEntry("mapping", "initial-failure"))
              },
              isNull(),
            )
          }
          // but will not create mapping between DPS and NOMIS ids as it already exists
          financeMappingApiMockServer.verify(
            0,
            postRequestedFor(urlPathEqualTo("/mapping/transactions")),
          )
        }
      }

      @Nested
      @DisplayName("When mapping POST fails")
      inner class MappingFail {

        val nomisTransactions = nomisTransactions()

        @BeforeEach
        fun setUp() {
          financeNomisApiMockServer.stubGetOffenderTransaction(
            bookingId = BOOKING_ID,
            transactionId = NOMIS_TRANSACTION_ID,
            response = nomisTransactions,
          )
          financeMappingApiMockServer.stubGetByNomisId(status = NOT_FOUND)
          financeApi.stubPostOffenderTransaction(
            SyncTransactionReceipt(
              synchronizedTransactionId = dpsTransactionUuid,
              requestId = UUID.randomUUID(),
              action = SyncTransactionReceipt.Action.CREATED,
            ),
          )
        }

        @Nested
        @DisplayName("Fails once")
        inner class FailsOnce {
          @BeforeEach
          fun setUp() {
            financeMappingApiMockServer.stubPostMappingFailureFollowedBySuccess()

            financeOffenderEventsQueue.sendMessage(
              offenderTransactionEvent(
                "OFFENDER_TRANSACTIONS-INSERTED",
                messageUuid,
              ),
            )
          }

          @Test
          fun `will create transaction in DPS`() {
            await untilAsserted {
              financeApi.verify(
                postRequestedFor(urlPathEqualTo("/sync/offender-transactions")),
              )
            }
          }

          @Test
          fun `will attempt to create mapping twice and succeed`() {
            await untilAsserted {
              financeMappingApiMockServer.verify(
                exactly(2),
                postRequestedFor(urlPathEqualTo("/mapping/transactions"))
                  .withRequestBody(matchingJsonPath("dpsTransactionId", equalTo(DPS_TRANSACTION_ID)))
                  .withRequestBody(matchingJsonPath("nomisBookingId", equalTo(BOOKING_ID.toString())))
                  .withRequestBody(matchingJsonPath("nomisTransactionId", equalTo(NOMIS_TRANSACTION_ID.toString())))
                  .withRequestBody(matchingJsonPath("mappingType", equalTo("NOMIS_CREATED"))),
              )
            }

            assertThat(
              awsSqsFinanceOffenderEventsDlqClient.countAllMessagesOnQueue(financeQueueOffenderEventsDlqUrl).get(),
            ).isEqualTo(0)
          }

          @Test
          fun `will track a telemetry event for partial success`() {
            await untilAsserted {
              verify(telemetryClient).trackEvent(
                eq("transactions-synchronisation-created-success"),
                check {
                  assertThat(it["bookingId"]).isEqualTo(BOOKING_ID.toString())
                  assertThat(it["nomisTransactionId"]).isEqualTo(NOMIS_TRANSACTION_ID.toString())
                  assertThat(it["dpsTransactionId"]).isEqualTo(DPS_TRANSACTION_ID)
                  assertThat(it["mapping"]).isEqualTo("initial-failure")
                  assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
                },
                isNull(),
              )
            }

            await untilAsserted {
              verify(telemetryClient).trackEvent(
                eq("transactions-mapping-created-synchronisation-success"),
                check {
                  assertThat(it["bookingId"]).isEqualTo(BOOKING_ID.toString())
                  assertThat(it["nomisTransactionId"]).isEqualTo(NOMIS_TRANSACTION_ID.toString())
                  assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
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
            financeMappingApiMockServer.stubPostMapping(status = INTERNAL_SERVER_ERROR)
            financeOffenderEventsQueue.sendMessage(
              offenderTransactionEvent(
                "OFFENDER_TRANSACTIONS-INSERTED",
                messageUuid,
              ),
            )
            await untilCallTo {
              awsSqsFinanceOffenderEventsDlqClient.countAllMessagesOnQueue(financeQueueOffenderEventsDlqUrl).get()
            } matches { it == 1 }
          }

          @Test
          fun `will create transaction in DPS`() {
            await untilAsserted {
              financeApi.verify(
                1,
                postRequestedFor(urlPathEqualTo("/sync/offender-transactions")),
              )
            }
          }

          @Test
          fun `will attempt to create mapping several times and keep failing`() {
            financeMappingApiMockServer.verify(
              exactly(3),
              postRequestedFor(urlPathEqualTo("/mapping/transactions")),
            )
          }

          @Test
          fun `will track a telemetry event for success`() {
            await untilAsserted {
              verify(telemetryClient).trackEvent(
                eq("transactions-synchronisation-created-success"),
                check {
                  assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
                  assertThat(it["bookingId"]).isEqualTo(BOOKING_ID.toString())
                  assertThat(it["nomisTransactionId"]).isEqualTo(NOMIS_TRANSACTION_ID.toString())
                  assertThat(it["dpsTransactionId"]).isEqualTo(DPS_TRANSACTION_ID)
                  assertThat(it["mapping"]).isEqualTo("initial-failure")
                },
                isNull(),
              )
            }
          }
        }
      }

      @Nested
      @DisplayName("When finance api POST fails")
      inner class FinanceApiFail {
        val nomisTransactions = nomisTransactions()

        @BeforeEach
        fun setUp() {
          financeNomisApiMockServer.stubGetOffenderTransaction(
            bookingId = BOOKING_ID,
            transactionId = NOMIS_TRANSACTION_ID,
            response = nomisTransactions,
          )
          financeApi.stubPostOffenderTransactionFailure()

          financeOffenderEventsQueue.sendMessage(
            offenderTransactionEvent(
              "OFFENDER_TRANSACTIONS-INSERTED",
              messageUuid,
            ),
          )
        }

        @Test
        fun `will not attempt to create mapping and will track a telemetry event for failure`() {
          await untilAsserted {
            verify(telemetryClient, atLeast(2)).trackEvent(
              // ******* was at least 1
              eq("transactions-synchronisation-created-failed"),
              check {
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
                assertThat(it["bookingId"]).isEqualTo(BOOKING_ID.toString())
                assertThat(it["nomisTransactionId"]).isEqualTo(NOMIS_TRANSACTION_ID.toString())
                assertThat(it["error"]).isEqualTo("500 Internal Server Error from POST http://localhost:8102/sync/offender-transactions")
              },
              isNull(),
            )
          }
          financeMappingApiMockServer.verify(
            0,
            postRequestedFor(urlPathEqualTo("/mapping/transactions")),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("OFFENDER_TRANSACTIONS-UPDATED")
  inner class PrisonerTransactionUpdated {
    // TODO
  }

  private fun Any.toJson(): String = jsonMapper.writeValueAsString(this)

  fun offenderTransactionEvent(
    eventType: String,
    messageId: UUID,
    bookingId: Long = BOOKING_ID,
    transactionId: Long = NOMIS_TRANSACTION_ID,
    offenderNo: String = OFFENDER_ID_DISPLAY,
    auditModuleName: String = "OIDNOMIS",
  ) = SQSMessage(
    MessageId = "$messageId",
    Type = "Notification",
    Message = TransactionEvent(
      transactionId = transactionId,
      entrySequence = 1,
      caseload = "SWI",
      offenderIdDisplay = offenderNo,
      bookingId = bookingId,
      auditModuleName = auditModuleName,
    ).toJson(),
    MessageAttributes = MessageAttributes(EventType(eventType, "String")),
  ).toJson()
}
