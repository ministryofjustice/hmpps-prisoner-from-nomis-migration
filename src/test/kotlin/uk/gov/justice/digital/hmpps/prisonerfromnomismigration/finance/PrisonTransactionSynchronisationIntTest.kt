package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.exactly
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
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
import org.mockito.Mockito.atLeastOnce
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.check
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.FinanceApiExtension.Companion.financeApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.PrisonerTransactionSynchronisationIntTest.Companion.OFFENDER_ID
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.model.SyncTransactionReceipt
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.EventType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MessageAttributes
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.SQSMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TransactionMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.TransactionIdBufferRepository
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath
import uk.gov.justice.hmpps.sqs.countAllMessagesOnQueue
import uk.gov.justice.hmpps.sqs.countMessagesOnQueue
import java.util.AbstractMap.SimpleEntry
import java.util.UUID

class PrisonTransactionSynchronisationIntTest : FinanceIntegrationTestBase() {

  companion object {
    internal const val BOOKING_ID = 1234L
    internal const val NOMIS_TRANSACTION_ID = 2345678L
    internal const val OFFENDER_ID_DISPLAY = "A3864DZ"
    internal const val DPS_TRANSACTION_ID = "a04f7a8d-61aa-400c-9395-000011112222"
    internal const val MESSAGE_ID = "abcdef01-0000-1111-2222-000011112222"
    val dpsTransactionUuid: UUID = UUID.fromString(DPS_TRANSACTION_ID)
    val messageUuid: UUID = UUID.fromString(MESSAGE_ID)
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
  @DisplayName("GL_TRANSACTIONS-INSERTED")
  inner class GeneralLedgerTransactionInserted {

    @BeforeEach
    fun setUp() = runTest {
      transactionIdBufferRepository.deleteAll()
    }

    @Nested
    @DisplayName("When transaction was created in DPS")
    inner class DPSCreated {

      @BeforeEach
      fun setUp() {
        val message = glTransactionEvent(
          messageId = messageUuid,
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
              assertThat(it["glSequence"]).isEqualTo("1")
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
      @DisplayName("Happy path where transaction has no associated offender transaction")
      inner class HappyPathGLTransactionNoOffenderTx {

        @BeforeEach
        fun setUp() {
          financeNomisApiMockServer.stubGetPrisonerTransaction(
            transactionId = NOMIS_TRANSACTION_ID,
            response = emptyList(),
          )
          financeMappingApiMockServer.stubPostMapping()

          financeOffenderEventsQueue.sendMessage(
            glTransactionEvent(
              messageId = messageUuid,
              offenderNo = null,
            ),
          )

          waitForAnyProcessingToComplete("transactions-synchronisation-gl-created-ignored")
        }

        @Test
        fun `will not create transaction in DPS`() {
          financeApi.verify(
            0,
            postRequestedFor(urlPathEqualTo("/sync/offender-transactions")),
          )
        }

        @Test
        fun `will not create mapping between DPS and NOMIS ids`() {
          financeMappingApiMockServer.verify(
            0,
            postRequestedFor(urlPathEqualTo("/mapping/transactions")),
          )
        }

        @Test
        fun `will track a telemetry event for success`() {
          verify(telemetryClient).trackEvent(
            eq("transactions-synchronisation-gl-created-ignored"),
            check {
              assertThat(it["bookingId"]).isEqualTo(BOOKING_ID.toString())
              assertThat(it["nomisTransactionId"]).isEqualTo(NOMIS_TRANSACTION_ID.toString())
            },
            isNull(),
          )
        }
      }

      @Nested
      @DisplayName("Happy path where transaction does not already exist in DPS")
      inner class HappyPathGLTransactionNotExistsInDps {
        val receipt = SyncTransactionReceipt(
          synchronizedTransactionId = dpsTransactionUuid,
          requestId = UUID.randomUUID(),
          action = SyncTransactionReceipt.Action.CREATED,
        )

        @BeforeEach
        fun setUp() {
          financeNomisApiMockServer.stubGetPrisonerTransaction(
            transactionId = NOMIS_TRANSACTION_ID,
            bookingId = BOOKING_ID,
          )
          financeApi.stubPostPrisonerTransaction(receipt)
          financeMappingApiMockServer.stubPostMapping()

          financeOffenderEventsQueue.sendMessage(
            glTransactionEvent(
              messageId = messageUuid,
            ),
          )
        }

        @Test
        fun `will create transaction in DPS`() {
          await untilAsserted {
            val t1 = nomisTransactions().first()
            val g1 = t1.generalLedgerTransactions.first()
            financeApi.verify(
              1,
              postRequestedFor(urlPathEqualTo("/sync/offender-transactions"))
                .withRequestBodyJsonPath("transactionId", PrisonerTransactionSynchronisationIntTest.NOMIS_TRANSACTION_ID)
                .withRequestBodyJsonPath("requestId", PrisonerTransactionSynchronisationIntTest.MESSAGE_ID)
                .withRequestBodyJsonPath("caseloadId", "SWI")
                .withRequestBodyJsonPath("transactionTimestamp", g1.transactionTimestamp)
                .withRequestBodyJsonPath("createdAt", t1.createdAt)
                .withRequestBodyJsonPath("createdBy", t1.createdBy)
                .withRequestBodyJsonPath("createdByDisplayName", t1.createdByDisplayName)
                .withRequestBodyJsonPath("lastModifiedAt", t1.lastModifiedAt.toString())
                .withRequestBodyJsonPath("lastModifiedBy", t1.lastModifiedBy.toString())
                .withRequestBodyJsonPath("lastModifiedByDisplayName", t1.lastModifiedByDisplayName.toString())
                .withRequestBodyJsonPath("offenderTransactions[0].entrySequence", equalTo(t1.transactionEntrySequence.toString()))
                .withRequestBodyJsonPath("offenderTransactions[0].offenderId", OFFENDER_ID)
                .withRequestBodyJsonPath(
                  "offenderTransactions[0].offenderDisplayId",
                  PrisonerTransactionSynchronisationIntTest.OFFENDER_ID_DISPLAY,
                )
                .withRequestBodyJsonPath("offenderTransactions[0].subAccountType", t1.subAccountType.name)
                .withRequestBodyJsonPath("offenderTransactions[0].postingType", t1.postingType.name)
                .withRequestBodyJsonPath("offenderTransactions[0].type", t1.type)
                .withRequestBodyJsonPath("offenderTransactions[0].description", t1.description)
                .withRequestBodyJsonPath("offenderTransactions[0].amount", "5.42")
                .withRequestBodyJsonPath("offenderTransactions[0].offenderBookingId", t1.bookingId.toString())
                .withRequestBodyJsonPath("offenderTransactions[0].reference", equalTo(t1.reference))
                .withRequestBodyJsonPath("offenderTransactions[0].generalLedgerEntries[0].entrySequence", g1.generalLedgerEntrySequence.toString())
                .withRequestBodyJsonPath("offenderTransactions[0].generalLedgerEntries[0].code", g1.accountCode)
                .withRequestBodyJsonPath("offenderTransactions[0].generalLedgerEntries[0].postingType", g1.postingType.name)
                .withRequestBodyJsonPath("offenderTransactions[0].generalLedgerEntries[0].amount", "6.71"),
            )
          }
        }

        @Test
        fun `will create mapping between DPS and NOMIS ids`() {
          await untilAsserted {
            financeMappingApiMockServer.verify(
              postRequestedFor(urlPathEqualTo("/mapping/transactions"))
                .withRequestBodyJsonPath("dpsTransactionId", DPS_TRANSACTION_ID)
                .withRequestBodyJsonPath("nomisBookingId", BOOKING_ID)
                .withRequestBodyJsonPath("offenderNo", OFFENDER_ID_DISPLAY)
                .withRequestBodyJsonPath("nomisTransactionId", NOMIS_TRANSACTION_ID.toString())
                .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED"),
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
      @DisplayName("Happy path where the associated offender transaction id already exists in DPS")
      inner class HappyPathGLTransactionExistsInDps {
        val receipt = SyncTransactionReceipt(
          synchronizedTransactionId = dpsTransactionUuid,
          requestId = UUID.randomUUID(),
          action = SyncTransactionReceipt.Action.UPDATED,
        )

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
          financeNomisApiMockServer.stubGetPrisonerTransaction(
            transactionId = NOMIS_TRANSACTION_ID,
            bookingId = BOOKING_ID,
          )
          financeApi.stubPostPrisonerTransaction(receipt)

          financeMappingApiMockServer.stubPostMapping()

          financeOffenderEventsQueue.sendMessage(
            glTransactionEvent(
              messageId = messageUuid,
            ),
          )
        }

        @Test
        fun `will update transaction in DPS`() {
          await untilAsserted {
            val t1 = nomisTransactions().first()
            val g1 = t1.generalLedgerTransactions.first()
            financeApi.verify(
              1,
              postRequestedFor(urlPathEqualTo("/sync/offender-transactions"))
                .withRequestBodyJsonPath("transactionId", PrisonerTransactionSynchronisationIntTest.NOMIS_TRANSACTION_ID)
                .withRequestBodyJsonPath("requestId", PrisonerTransactionSynchronisationIntTest.MESSAGE_ID)
                .withRequestBodyJsonPath("caseloadId", "SWI")
                .withRequestBodyJsonPath("transactionTimestamp", g1.transactionTimestamp)
                .withRequestBodyJsonPath("createdAt", t1.createdAt)
                .withRequestBodyJsonPath("createdBy", t1.createdBy)
                .withRequestBodyJsonPath("createdByDisplayName", t1.createdByDisplayName)
                .withRequestBodyJsonPath("lastModifiedAt", t1.lastModifiedAt.toString())
                .withRequestBodyJsonPath("lastModifiedBy", t1.lastModifiedBy.toString())
                .withRequestBodyJsonPath("lastModifiedByDisplayName", t1.lastModifiedByDisplayName.toString())
                .withRequestBodyJsonPath("offenderTransactions[0].entrySequence", equalTo(t1.transactionEntrySequence.toString()))
                .withRequestBodyJsonPath("offenderTransactions[0].offenderId", OFFENDER_ID)
                .withRequestBodyJsonPath(
                  "offenderTransactions[0].offenderDisplayId",
                  PrisonerTransactionSynchronisationIntTest.OFFENDER_ID_DISPLAY,
                )
                .withRequestBodyJsonPath("offenderTransactions[0].subAccountType", t1.subAccountType.name)
                .withRequestBodyJsonPath("offenderTransactions[0].postingType", t1.postingType.name)
                .withRequestBodyJsonPath("offenderTransactions[0].type", t1.type)
                .withRequestBodyJsonPath("offenderTransactions[0].description", t1.description)
                .withRequestBodyJsonPath("offenderTransactions[0].amount", "5.42")
                .withRequestBodyJsonPath("offenderTransactions[0].offenderBookingId", t1.bookingId.toString())
                .withRequestBodyJsonPath("offenderTransactions[0].reference", equalTo(t1.reference))
                .withRequestBodyJsonPath("offenderTransactions[0].generalLedgerEntries[0].entrySequence", g1.generalLedgerEntrySequence.toString())
                .withRequestBodyJsonPath("offenderTransactions[0].generalLedgerEntries[0].code", g1.accountCode)
                .withRequestBodyJsonPath("offenderTransactions[0].generalLedgerEntries[0].postingType", g1.postingType.name)
                .withRequestBodyJsonPath("offenderTransactions[0].generalLedgerEntries[0].amount", "6.71"),
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
    }
  }

  @Nested
  @DisplayName("GL_TRANSACTIONS-UPDATED")
  inner class GeneralLedgerTransactionUpdated {

    @Nested
    @DisplayName("When transaction was created in DPS")
    inner class DPSCreated {

      @BeforeEach
      fun setUp() {
        val message = glTransactionEvent(
          eventType = "GL_TRANSACTIONS-UPDATED",
          messageId = messageUuid,
          bookingId = BOOKING_ID,
          offenderNo = OFFENDER_ID_DISPLAY,
          auditModuleName = "DPS_SYNCHRONISATION",
        )
        financeOffenderEventsQueue.sendMessage(message)
      }

      @Test
      fun `the event is ignored`() {
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("transactions-synchronisation-updated-skipped"),
            check {
              assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
              assertThat(it["bookingId"]).isEqualTo(BOOKING_ID.toString())
              assertThat(it["nomisTransactionId"]).isEqualTo(NOMIS_TRANSACTION_ID.toString())
              assertThat(it["glSequence"]).isEqualTo("1")
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
      @DisplayName("Happy path where transaction has no associated offender transaction")
      inner class HappyPathGLTransactionNoOffenderTx {

        @BeforeEach
        fun setUp() {
          financeMappingApiMockServer.stubGetByNomisId(NOMIS_TRANSACTION_ID)
          financeNomisApiMockServer.stubGetPrisonerTransaction(
            transactionId = NOMIS_TRANSACTION_ID,
            response = emptyList(),
          )
          financeMappingApiMockServer.stubPostMapping()

          financeOffenderEventsQueue.sendMessage(
            glTransactionEvent(
              eventType = "GL_TRANSACTIONS-UPDATED",
              messageId = messageUuid,
              offenderNo = null,
            ),
          )

          waitForAnyProcessingToComplete("transactions-synchronisation-gl-updated-ignored")
        }

        @Test
        fun `will not create transaction in DPS`() {
          financeApi.verify(
            0,
            postRequestedFor(urlPathEqualTo("/sync/offender-transactions")),
          )
        }

        @Test
        fun `will not create mapping between DPS and NOMIS ids`() {
          financeMappingApiMockServer.verify(
            0,
            postRequestedFor(urlPathEqualTo("/mapping/transactions")),
          )
        }

        @Test
        fun `will track a telemetry event for success`() {
          verify(telemetryClient).trackEvent(
            eq("transactions-synchronisation-gl-updated-ignored"),
            check {
              assertThat(it["bookingId"]).isEqualTo(BOOKING_ID.toString())
              assertThat(it["nomisTransactionId"]).isEqualTo(NOMIS_TRANSACTION_ID.toString())
            },
            isNull(),
          )
        }
      }

      @Nested
      @DisplayName("Happy path where the associated offender transaction id already exists in DPS")
      inner class HappyPathGLTransactionExistsInDps {
        val receipt = SyncTransactionReceipt(
          synchronizedTransactionId = dpsTransactionUuid,
          requestId = UUID.randomUUID(),
          action = SyncTransactionReceipt.Action.UPDATED,
        )

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
          financeNomisApiMockServer.stubGetPrisonerTransaction(
            transactionId = NOMIS_TRANSACTION_ID,
            bookingId = BOOKING_ID,
          )
          financeApi.stubPostPrisonerTransaction(receipt)
          financeMappingApiMockServer.stubPostMapping()

          financeOffenderEventsQueue.sendMessage(
            glTransactionEvent(
              eventType = "GL_TRANSACTIONS-UPDATED",
              messageId = messageUuid,
            ),
          )
        }

        @Test
        fun `will update transaction in DPS`() {
          await untilAsserted {
            val t1 = nomisTransactions().first()
            val g1 = t1.generalLedgerTransactions.first()
            financeApi.verify(
              1,
              postRequestedFor(urlPathEqualTo("/sync/offender-transactions"))
                .withRequestBodyJsonPath("transactionId", PrisonerTransactionSynchronisationIntTest.NOMIS_TRANSACTION_ID)
                .withRequestBodyJsonPath("requestId", PrisonerTransactionSynchronisationIntTest.MESSAGE_ID)
                .withRequestBodyJsonPath("caseloadId", "SWI")
                .withRequestBodyJsonPath("transactionTimestamp", g1.transactionTimestamp)
                .withRequestBodyJsonPath("createdAt", t1.createdAt)
                .withRequestBodyJsonPath("createdBy", t1.createdBy)
                .withRequestBodyJsonPath("createdByDisplayName", t1.createdByDisplayName)
                .withRequestBodyJsonPath("lastModifiedAt", t1.lastModifiedAt.toString())
                .withRequestBodyJsonPath("lastModifiedBy", t1.lastModifiedBy.toString())
                .withRequestBodyJsonPath("lastModifiedByDisplayName", t1.lastModifiedByDisplayName.toString())
                .withRequestBodyJsonPath("offenderTransactions[0].entrySequence", equalTo(t1.transactionEntrySequence.toString()))
                .withRequestBodyJsonPath("offenderTransactions[0].offenderId", OFFENDER_ID)
                .withRequestBodyJsonPath(
                  "offenderTransactions[0].offenderDisplayId",
                  PrisonerTransactionSynchronisationIntTest.OFFENDER_ID_DISPLAY,
                )
                .withRequestBodyJsonPath("offenderTransactions[0].subAccountType", t1.subAccountType.name)
                .withRequestBodyJsonPath("offenderTransactions[0].postingType", t1.postingType.name)
                .withRequestBodyJsonPath("offenderTransactions[0].type", t1.type)
                .withRequestBodyJsonPath("offenderTransactions[0].description", t1.description)
                .withRequestBodyJsonPath("offenderTransactions[0].amount", "5.42")
                .withRequestBodyJsonPath("offenderTransactions[0].offenderBookingId", t1.bookingId.toString())
                .withRequestBodyJsonPath("offenderTransactions[0].reference", equalTo(t1.reference))
                .withRequestBodyJsonPath("offenderTransactions[0].generalLedgerEntries[0].entrySequence", g1.generalLedgerEntrySequence.toString())
                .withRequestBodyJsonPath("offenderTransactions[0].generalLedgerEntries[0].code", g1.accountCode)
                .withRequestBodyJsonPath("offenderTransactions[0].generalLedgerEntries[0].postingType", g1.postingType.name)
                .withRequestBodyJsonPath("offenderTransactions[0].generalLedgerEntries[0].amount", "6.71"),
            )
          }
        }

        @Test
        fun `will track a telemetry event for success`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("transactions-synchronisation-updated-success-additional"),
              check {
                assertThat(it["nomisTransactionId"]).isEqualTo(NOMIS_TRANSACTION_ID.toString())
                assertThat(it["bookingId"]).isEqualTo(BOOKING_ID.toString())
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
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

    @Nested
    @DisplayName("No mapping exists")
    inner class HappyPathNoMapping {
      @BeforeEach
      fun setUp() {
        financeMappingApiMockServer.stubGetByNomisId(mapping = null)

        financeOffenderEventsQueue.sendMessage(
          glTransactionEvent(
            "GL_TRANSACTIONS-UPDATED",
            messageUuid,
          ),
        )
        await untilCallTo { awsSqsFinanceOffenderEventsDlqClient.countMessagesOnQueue(financeQueueOffenderEventsDlqUrl).get() } matches { it == 1 }
      }

      @Test
      fun `will attempt to get the mapping between DPS and NOMIS ids more than once due to retry`() {
        financeMappingApiMockServer.verify(
          exactly(2),
          getRequestedFor(urlPathMatching("/mapping/transactions/nomis-transaction-id/$NOMIS_TRANSACTION_ID")),
        )
      }

      @Test
      fun `will not update transaction in DPS`() {
        financeApi.verify(0, postRequestedFor(urlPathEqualTo("/sync/offender-transactions")))
      }

      @Test
      fun `will track a telemetry event for failure`() {
        await untilAsserted {
          verify(telemetryClient, atLeastOnce()).trackEvent(
            eq("transactions-synchronisation-updated-failed"),
            check {
              assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
              assertThat(it["bookingId"]).isEqualTo(BOOKING_ID.toString())
              assertThat(it["nomisTransactionId"]).isEqualTo(NOMIS_TRANSACTION_ID.toString())
              assertThat(it).doesNotContainKey("dpsTransactionId")
              assertThat(it).doesNotContain(SimpleEntry("mapping", "initial-failure"))
            },
            isNull(),
          )
        }
        // and will not create mapping between DPS and NOMIS ids as it already exists
        financeMappingApiMockServer.verify(
          0,
          postRequestedFor(urlPathEqualTo("/mapping/transactions")),
        )
      }

      @Test
      fun `the event is placed on dead letter queue`() {
        await untilAsserted {
          assertThat(
            awsSqsFinanceOffenderEventsDlqClient.countAllMessagesOnQueue(financeQueueOffenderEventsDlqUrl).get(),
          ).isEqualTo(1)
        }
      }
    }

    @Nested
    @DisplayName("When finance api POST fails")
    inner class FinanceApiFail {

      @BeforeEach
      fun setUp() {
        financeOffenderEventsQueue.sendMessage(
          glTransactionEvent("GL_TRANSACTIONS-UPDATED", messageUuid),
        )
      }

      @Test
      fun `will not attempt to create mapping and will track a telemetry event for failure`() {
        await untilAsserted {
          verify(telemetryClient, atLeast(2)).trackEvent(
            eq("transactions-synchronisation-updated-failed"),
            check {
              assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
              assertThat(it["nomisTransactionId"]).isEqualTo(NOMIS_TRANSACTION_ID.toString())
            },
            isNull(),
          )
        }
        // but will not create a mapping between DPS and NOMIS ids as it already exists
        financeMappingApiMockServer.verify(
          0,
          postRequestedFor(urlPathEqualTo("/mapping/transactions")),
        )
      }
    }
  }

  private fun Any.toJson(): String = jsonMapper.writeValueAsString(this)

  fun glTransactionEvent(
    eventType: String = "GL_TRANSACTIONS-INSERTED",
    messageId: UUID,
    bookingId: Long = BOOKING_ID,
    transactionId: Long = NOMIS_TRANSACTION_ID,
    offenderNo: String? = OFFENDER_ID_DISPLAY,
    auditModuleName: String = "OTDSUBAT",
  ) = SQSMessage(
    MessageId = "$messageId",
    Type = "Notification",
    Message = GLTransactionEvent(
      transactionId = transactionId,
      entrySequence = 1,
      gLEntrySequence = 1,
      caseload = "SWI",
      offenderIdDisplay = offenderNo,
      bookingId = bookingId,
      auditModuleName = auditModuleName,
    ).toJson(),
    MessageAttributes = MessageAttributes(EventType(eventType, "String")),
  ).toJson()
}
