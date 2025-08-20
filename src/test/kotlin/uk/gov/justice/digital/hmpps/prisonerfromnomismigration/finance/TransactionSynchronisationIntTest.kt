package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance

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
import org.mockito.ArgumentMatchers.eq
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.check
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.http.HttpStatus.NOT_FOUND
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.FinanceApiExtension.Companion.financeApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.model.SyncTransactionReceipt
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TransactionMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.GeneralLedgerTransactionDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderTransactionDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath
import uk.gov.justice.hmpps.sqs.countAllMessagesOnQueue
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import java.util.AbstractMap.SimpleEntry

private const val BOOKING_ID = 1234L
private const val NOMIS_TRANSACTION_ID = 2345678L
private const val OFFENDER_ID = 101L
private const val OFFENDER_ID_DISPLAY = "A3864DZ"
private const val DPS_TRANSACTION_ID = "a04f7a8d-61aa-400c-9395-000011112222"
private const val MESSAGE_ID = "abcdef01-0000-1111-2222-000011112222"

class TransactionSynchronisationIntTest : SqsIntegrationTestBase() {

  private val dpsTransactionUuid = UUID.fromString(DPS_TRANSACTION_ID)
  private val messageUuid = UUID.fromString(MESSAGE_ID)

  @Autowired
  private lateinit var financeNomisApiMockServer: FinanceNomisApiMockServer

  @Autowired
  private lateinit var financeMappingApiMockServer: FinanceMappingApiMockServer

  @Nested
  @DisplayName("OFFENDER_TRANSACTIONS-INSERTED")
  inner class TransactionInserted {

    @Nested
    @DisplayName("When transaction was created in DPS")
    inner class DPSCreated {

      @BeforeEach
      fun setUp() {
        financeOffenderEventsQueue.sendMessage(
          offenderTransactionEvent(
            "OFFENDER_TRANSACTIONS-INSERTED", // and GL_TRANSACTIONS-INSERTED
            messageUuid,
            bookingId = BOOKING_ID,
            offenderNo = OFFENDER_ID_DISPLAY,
            auditModuleName = "DPS_SYNCHRONISATION",
          ),
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

          awsSqsFinanceOffenderEventsClient.sendMessage(
            financeQueueOffenderEventsUrl,
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

          awsSqsFinanceOffenderEventsClient.sendMessage(
            financeQueueOffenderEventsUrl,
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

            awsSqsFinanceOffenderEventsClient.sendMessage(
              financeQueueOffenderEventsUrl,
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
            awsSqsFinanceOffenderEventsClient.sendMessage(
              financeQueueOffenderEventsUrl,
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

          awsSqsFinanceOffenderEventsClient.sendMessage(
            financeQueueOffenderEventsUrl,
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
  @DisplayName("GL_TRANSACTIONS-INSERTED")
  inner class GeneralLedgerTransactionInserted {

    @Nested
    @DisplayName("When transaction was created in NOMIS")
    inner class NomisCreated {
      @Nested
      @DisplayName("Happy path where transaction does not already exist in DPS")
      inner class HappyPathGLTransactionFirst {
        val receipt = SyncTransactionReceipt(
          synchronizedTransactionId = dpsTransactionUuid,
          requestId = UUID.randomUUID(),
          action = SyncTransactionReceipt.Action.CREATED,
        )

        val nomisGLTransactions = nomisGLTransactions()

        @BeforeEach
        fun setUp() {
          financeNomisApiMockServer.stubGetOffenderTransaction(
            transactionId = NOMIS_TRANSACTION_ID,
            response = emptyList(),
          )
          financeNomisApiMockServer.stubGetGLTransaction(
            transactionId = NOMIS_TRANSACTION_ID,
            response = nomisGLTransactions,
          )
          financeApi.stubPostGLTransaction(receipt)
          financeMappingApiMockServer.stubPostMapping()

          awsSqsFinanceOffenderEventsClient.sendMessage(
            financeQueueOffenderEventsUrl,
            glTransactionEvent(
              "GL_TRANSACTIONS-INSERTED",
              messageUuid,
            ),
          )
        }

        @Test
        fun `will create transaction in DPS`() {
          await untilAsserted {
            val g1 = nomisGLTransactions.first()
            financeApi.verify(
              1,
              postRequestedFor(urlPathEqualTo("/sync/general-ledger-transactions"))
                .withRequestBodyJsonPath("transactionId", equalTo(NOMIS_TRANSACTION_ID.toString()))
                .withRequestBodyJsonPath("requestId", equalTo(MESSAGE_ID))
                .withRequestBodyJsonPath("description", equalTo(g1.description))
                .withRequestBodyJsonPath("caseloadId", equalTo(g1.caseloadId))
                .withRequestBodyJsonPath("transactionType", equalTo(g1.type))
                .withRequestBodyJsonPath("transactionTimestamp", equalTo(g1.transactionTimestamp.toString()))
                .withRequestBodyJsonPath("createdAt", equalTo(g1.createdAt.toString()))
                .withRequestBodyJsonPath("createdBy", equalTo(g1.createdBy))
                .withRequestBodyJsonPath("createdByDisplayName", equalTo(g1.createdByDisplayName))
                .withRequestBodyJsonPath("reference", equalTo(g1.reference))
                .withRequestBodyJsonPath("lastModifiedAt", equalTo(g1.lastModifiedAt.toString()))
                .withRequestBodyJsonPath("lastModifiedBy", equalTo(g1.lastModifiedBy))
                .withRequestBodyJsonPath("lastModifiedByDisplayName", equalTo(g1.lastModifiedByDisplayName))
                .withRequestBodyJsonPath(
                  "generalLedgerEntries[0].entrySequence",
                  equalTo(g1.generalLedgerEntrySequence.toString()),
                )
                .withRequestBodyJsonPath("generalLedgerEntries[0].code", equalTo(g1.accountCode.toString()))
                .withRequestBodyJsonPath("generalLedgerEntries[0].postingType", equalTo(g1.postingType.name))
                .withRequestBodyJsonPath("generalLedgerEntries[0].amount", equalTo("5.4")),
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
              eq("transactions-synchronisation-created-success-gl"),
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
      inner class HappyPathGLTransactionNotFirst {
        val receipt = SyncTransactionReceipt(
          synchronizedTransactionId = dpsTransactionUuid,
          requestId = UUID.randomUUID(),
          action = SyncTransactionReceipt.Action.UPDATED,
        )

        val nomisGLTransactions = nomisGLTransactions()

        @BeforeEach
        fun setUp() {
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
          financeNomisApiMockServer.stubGetOffenderTransaction(
            transactionId = NOMIS_TRANSACTION_ID,
            response = emptyList(),
          )
          financeNomisApiMockServer.stubGetGLTransaction(
            transactionId = NOMIS_TRANSACTION_ID,
            response = nomisGLTransactions,
          )
          financeApi.stubPostGLTransaction(receipt)
          financeMappingApiMockServer.stubPostMapping()

          awsSqsFinanceOffenderEventsClient.sendMessage(
            financeQueueOffenderEventsUrl,
            glTransactionEvent(
              "GL_TRANSACTIONS-INSERTED",
              messageUuid,
            ),
          )
        }

        @Test
        fun `will update transaction in DPS`() {
          await untilAsserted {
            val g1 = nomisGLTransactions.first()
            financeApi.verify(
              1,
              postRequestedFor(urlPathEqualTo("/sync/general-ledger-transactions"))
                .withRequestBodyJsonPath("transactionId", equalTo(NOMIS_TRANSACTION_ID.toString()))
                .withRequestBodyJsonPath("requestId", equalTo(MESSAGE_ID))
                .withRequestBodyJsonPath("description", equalTo(g1.description))
                .withRequestBodyJsonPath("caseloadId", equalTo(g1.caseloadId))
                .withRequestBodyJsonPath("transactionType", equalTo(g1.type))
                .withRequestBodyJsonPath("transactionTimestamp", equalTo(g1.transactionTimestamp.toString()))
                .withRequestBodyJsonPath("createdAt", equalTo(g1.createdAt.toString()))
                .withRequestBodyJsonPath("createdBy", equalTo(g1.createdBy))
                .withRequestBodyJsonPath("createdByDisplayName", equalTo(g1.createdByDisplayName))
                .withRequestBodyJsonPath("reference", equalTo(g1.reference))
                .withRequestBodyJsonPath("lastModifiedAt", equalTo(g1.lastModifiedAt.toString()))
                .withRequestBodyJsonPath("lastModifiedBy", equalTo(g1.lastModifiedBy))
                .withRequestBodyJsonPath("lastModifiedByDisplayName", equalTo(g1.lastModifiedByDisplayName))
                .withRequestBodyJsonPath(
                  "generalLedgerEntries[0].entrySequence",
                  equalTo(g1.generalLedgerEntrySequence.toString()),
                )
                .withRequestBodyJsonPath("generalLedgerEntries[0].code", equalTo(g1.accountCode.toString()))
                .withRequestBodyJsonPath("generalLedgerEntries[0].postingType", equalTo(g1.postingType.name))
                .withRequestBodyJsonPath("generalLedgerEntries[0].amount", equalTo("5.4")),
            )
          }
        }

        @Test
        fun `will track a telemetry event for success`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("transactions-synchronisation-created-success-gl-additional"),
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
    }
  }
}

fun offenderTransactionEvent(
  eventType: String,
  messageId: UUID,
  bookingId: Long = BOOKING_ID,
  transactionId: Long = NOMIS_TRANSACTION_ID,
  offenderNo: String = OFFENDER_ID_DISPLAY,
  auditModuleName: String = "OIDNOMIS",
) = """{
    "MessageId": "$messageId", "Type": "Notification", "Timestamp": "2019-10-21T14:01:18.500Z", 
    "Message": "{\"eventType\":\"$eventType\",\"nomisEventType\":\"$eventType\",\"eventDatetime\":\"2024-07-10T15:00:25.0000000Z\",\"bookingId\": \"$bookingId\",\"transactionId\": \"$transactionId\",\"offenderIdDisplay\": \"$offenderNo\",\"auditModuleName\":\"$auditModuleName\",\"caseload\":\"SWI\",\"entrySequence\":\"1\" }",
    "TopicArn": "arn:aws:sns:eu-west-1:000000000000:offender_events", 
    "MessageAttributes": {
      "eventType": {"Type": "String", "Value": "$eventType"}, 
      "id": {"Type": "String", "Value": "8b07cbd9-0820-0a0f-c32f-a9429b618e0b"}, 
      "contentType": {"Type": "String", "Value": "text/plain;charset=UTF-8"}, 
      "timestamp": {"Type": "Number.java.lang.Long", "Value": "1571666478344"}
    }
}
""".trimIndent()

fun glTransactionEvent(
  eventType: String,
  messageId: UUID,
  bookingId: Long = BOOKING_ID,
  transactionId: Long = NOMIS_TRANSACTION_ID,
  offenderNo: String = OFFENDER_ID_DISPLAY,
) = """{
    "MessageId": "$messageId", "Type": "Notification", "Timestamp": "2019-10-21T14:01:18.500Z", 
    "Message": "{\"eventType\":\"$eventType\",\"nomisEventType\":\"$eventType\",\"eventDatetime\":\"2024-07-10T15:00:25.0000000Z\",\"bookingId\": \"$bookingId\",\"transactionId\": \"$transactionId\",\"offenderIdDisplay\": \"$offenderNo\",\"auditModuleName\":\"PRISON_API\",\"caseload\":\"SWI\",\"entrySequence\":\"1\",\"glentrySequence\":\"1\" }",
    "TopicArn": "arn:aws:sns:eu-west-1:000000000000:offender_events", 
    "MessageAttributes": {
      "eventType": {"Type": "String", "Value": "$eventType"}, 
      "id": {"Type": "String", "Value": "8b07cbd9-0820-0a0f-c32f-a9429b618e0b"}, 
      "contentType": {"Type": "String", "Value": "text/plain;charset=UTF-8"}, 
      "timestamp": {"Type": "Number.java.lang.Long", "Value": "1571666478344"}
    }
}
""".trimIndent()

private fun nomisTransactions(bookingId: Long = BOOKING_ID, transactionId: Long = NOMIS_TRANSACTION_ID) = listOf(
  OffenderTransactionDto(
    transactionId = transactionId,
    transactionEntrySequence = 1,
    offenderId = OFFENDER_ID,
    offenderNo = OFFENDER_ID_DISPLAY,
    caseloadId = "SWI",
    bookingId = bookingId,
    amount = BigDecimal(5.4),
    type = "type",
    postingType = OffenderTransactionDto.PostingType.CR,
    description = "desc",
    entryDate = LocalDate.parse("2021-02-03"),
    subAccountType = OffenderTransactionDto.SubAccountType.REG,
    generalLedgerTransactions = nomisGLTransactions(),
    createdAt = LocalDateTime.parse("2021-02-03T04:05:06"),
    createdBy = "me",
    createdByDisplayName = "Me",
    clientReference = "clientref",
    reference = "ref",
    lastModifiedAt = LocalDateTime.parse("2021-02-03T04:05:59"),
    lastModifiedBy = "you",
    lastModifiedByDisplayName = "You",
  ),
)

private fun nomisGLTransactions(transactionId: Long = NOMIS_TRANSACTION_ID) = listOf(
  GeneralLedgerTransactionDto(
    transactionId = transactionId,
    transactionEntrySequence = 1,
    generalLedgerEntrySequence = 1,
    caseloadId = "SWI",
    amount = BigDecimal(5.4),
    type = "type",
    postingType = GeneralLedgerTransactionDto.PostingType.CR,
    accountCode = 1021,
    description = "GL desc",
    transactionTimestamp = LocalDateTime.parse("2021-02-03T04:05:09"),
    reference = "ref",
    createdAt = LocalDateTime.parse("2021-02-03T04:05:07"),
    createdBy = "me",
    createdByDisplayName = "Me",
    lastModifiedAt = LocalDateTime.parse("2021-02-03T04:05:59"),
    lastModifiedBy = "you",
    lastModifiedByDisplayName = "You",
  ),
)
