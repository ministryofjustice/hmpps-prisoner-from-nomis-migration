package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.advances

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.exactly
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.serverError
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.atLeastOnce
import org.mockito.kotlin.check
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.test.context.TestPropertySource
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.FinanceApiExtension.Companion.financeApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.FinanceIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.FinanceMappingApiMockServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.FinanceNomisApiMockServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.TransactionEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.model.SyncTransactionReceipt
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.offenderTransactionDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorContent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.EventType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MessageAttributes
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.SQSMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TransactionMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.TransactionIdBufferRepository
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath
import uk.gov.justice.hmpps.sqs.countAllMessagesOnQueue
import java.math.BigDecimal
import java.util.AbstractMap.SimpleEntry
import java.util.UUID

@TestPropertySource(properties = ["offender_transactions.process.advances=true"])
class AdvancesSynchronisationRepayWriteOffIntTest(
  @Autowired private val financeNomisApiMockServer: FinanceNomisApiMockServer,
  @Autowired private val jsonMapper: JsonMapper,
  @Autowired private val financeMappingApiMockServer: FinanceMappingApiMockServer,
  @Autowired private val transactionIdBufferRepository: TransactionIdBufferRepository,
  @Autowired private val advancesNomisApiMockServer: AdvancesNomisApiMockServer,
  @Autowired private val advancesMappingApiMockServer: AdvancesMappingApiMockServer,
) : FinanceIntegrationTestBase() {

  companion object {
    internal const val BOOKING_ID = 1234L
    internal const val NOMIS_TRANSACTION_ID = 2345678L
    internal const val OFFENDER_ID = 101L
    internal const val OFFENDER_ID_DISPLAY = "A3864DZ"
    internal const val DPS_TRANSACTION_ID = "abcdef01-0000-1111-2222-000011112222"
    internal const val MESSAGE_ID = "abcdef01-1111-2222-3333-111122223333"
    val dpsTransactionUuid: UUID = UUID.fromString(DPS_TRANSACTION_ID)
    val messageUuid: UUID = UUID.fromString(MESSAGE_ID)
  }

  @BeforeEach
  fun clearTransactionIdBuffer() = runTest {
    transactionIdBufferRepository.deleteAll()
  }

  @Nested
  @DisplayName("Repay advance")
  inner class RepayAdvance {
    val dpsAdvanceId: UUID = UUID.randomUUID()
    val deductionId = 65432L
    val advance = prisonerAdvance(prisonNumber = OFFENDER_ID_DISPLAY)
    val receipt = SyncTransactionReceipt(
      synchronizedTransactionId = dpsTransactionUuid,
      requestId = UUID.randomUUID(),
      action = SyncTransactionReceipt.Action.CREATED,
    )
    val repaymentTransaction = offenderTransactionDto(bookingId = BOOKING_ID, transactionId = NOMIS_TRANSACTION_ID).let {
      it.copy(
        type = "DED",
        generalLedgerTransactions = it.generalLedgerTransactions.map { gl ->
          gl.copy(offenderDeductionId = deductionId)
        },
      )
    }

    @BeforeEach
    fun setUp() {
      financeNomisApiMockServer.stubGetPrisonerTransaction(
        bookingId = BOOKING_ID,
        transactionId = NOMIS_TRANSACTION_ID,
        response = listOf(repaymentTransaction),
      )
      financeApi.stubPostPrisonerTransaction(receipt)
      financeMappingApiMockServer.stubGetByNomisId(NOMIS_TRANSACTION_ID, mapping = null)
      financeMappingApiMockServer.stubPostMapping()
      advancesNomisApiMockServer.stubGetAdvanceByDeductionId(deductionId, advance)
      advancesMappingApiMockServer.stubGetAdvanceByNomisIdOrNull(
        nomisAdvanceId = advance.id,
        dpsId = dpsAdvanceId.toString(),
      )
      financeApi.stubRepayAdvance(advanceUuid = dpsAdvanceId)
    }

    @Nested
    @DisplayName("When the repayment originates in DPS")
    inner class DpsCreated {
      @BeforeEach
      fun setUp() {
        financeOffenderEventsQueue.sendMessage(
          offenderTransactionEvent(messageId = messageUuid, auditModuleName = "DPS_SYNCHRONISATION"),
        )
        waitForAnyProcessingToComplete("transactions-synchronisation-created-skipped")
      }

      @Test
      fun `will not look up or repay the advance`() {
        advancesNomisApiMockServer.verify(0, getRequestedFor(urlPathEqualTo("/finance/advances/deduction-id/$deductionId")))
        financeApi.verify(0, postRequestedFor(anyUrl()))
      }
    }

    @Nested
    @DisplayName("Happy path for advance repayment")
    inner class HappyPathRepayAdvance {
      @BeforeEach
      fun setUp() {
        sendRepaymentEvent()
        waitForAnyProcessingToComplete("transactions-synchronisation-created-success", "finance-advances-synchronisation-updated-success")
      }

      @Test
      fun `will create repayment transaction in DPS`() {
        val t1 = repaymentTransaction
        val g1 = t1.generalLedgerTransactions.first()
        financeApi.verify(
          1,
          postRequestedFor(urlPathEqualTo("/sync/offender-transactions"))
            .withRequestBodyJsonPath("transactionId", NOMIS_TRANSACTION_ID)
            .withRequestBodyJsonPath("requestId", MESSAGE_ID)
            .withRequestBodyJsonPath("caseloadId", "SWI")
            .withRequestBodyJsonPath("transactionTimestamp", g1.transactionTimestamp)
            .withRequestBodyJsonPath("createdAt", t1.createdAt)
            .withRequestBodyJsonPath("createdBy", t1.createdBy)
            .withRequestBodyJsonPath("createdByDisplayName", t1.createdByDisplayName)
            .withRequestBodyJsonPath("lastModifiedAt", t1.lastModifiedAt.toString())
            .withRequestBodyJsonPath("lastModifiedBy", t1.lastModifiedBy.toString())
            .withRequestBodyJsonPath("lastModifiedByDisplayName", t1.lastModifiedByDisplayName.toString())
            .withRequestBodyJsonPath("offenderTransactions[0].entrySequence", t1.transactionEntrySequence)
            .withRequestBodyJsonPath("offenderTransactions[0].offenderId", OFFENDER_ID)
            .withRequestBodyJsonPath("offenderTransactions[0].offenderDisplayId", OFFENDER_ID_DISPLAY)
            .withRequestBodyJsonPath("offenderTransactions[0].subAccountType", t1.subAccountType.name)
            .withRequestBodyJsonPath("offenderTransactions[0].postingType", t1.postingType.name)
            .withRequestBodyJsonPath("offenderTransactions[0].type", "DED")
            .withRequestBodyJsonPath("offenderTransactions[0].description", t1.description)
            .withRequestBodyJsonPath("offenderTransactions[0].amount", "5.42")
            .withRequestBodyJsonPath("offenderTransactions[0].offenderBookingId", BOOKING_ID)
            .withRequestBodyJsonPath("offenderTransactions[0].reference", equalTo(t1.reference))
            .withRequestBodyJsonPath("offenderTransactions[0].generalLedgerEntries[0].entrySequence", g1.generalLedgerEntrySequence)
            .withRequestBodyJsonPath("offenderTransactions[0].generalLedgerEntries[0].code", g1.accountCode)
            .withRequestBodyJsonPath("offenderTransactions[0].generalLedgerEntries[0].postingType", g1.postingType.name)
            .withRequestBodyJsonPath("offenderTransactions[0].generalLedgerEntries[0].amount", "6.71"),
        )
      }

      @Test
      fun `will create mapping between DPS and NOMIS ids`() {
        financeMappingApiMockServer.verify(
          1,
          postRequestedFor(urlPathEqualTo("/mapping/transactions"))
            .withRequestBodyJsonPath("dpsTransactionId", DPS_TRANSACTION_ID)
            .withRequestBodyJsonPath("nomisBookingId", BOOKING_ID)
            .withRequestBodyJsonPath("offenderNo", OFFENDER_ID_DISPLAY)
            .withRequestBodyJsonPath("nomisTransactionId", NOMIS_TRANSACTION_ID)
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED"),
        )
      }

      @Test
      fun `will track a transaction telemetry event for success`() {
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

      @Test
      fun `will look up the advance by deduction id and repay it in DPS`() {
        advancesNomisApiMockServer.verify(
          1,
          getRequestedFor(urlPathEqualTo("/finance/advances/deduction-id/$deductionId")),
        )
        advancesMappingApiMockServer.verify(
          1,
          getRequestedFor(urlPathEqualTo("/mapping/advances/nomis-id/${advance.id}")),
        )
        verifyRepayment()
      }

      @Test
      fun `will track a repayment telemetry event for success`() {
        verify(telemetryClient).trackEvent(
          eq("finance-advances-synchronisation-updated-success"),
          check {
            assertThat(it["prisonNumber"]).isEqualTo(OFFENDER_ID_DISPLAY)
            assertThat(it["nomisTransactionId"]).isEqualTo(NOMIS_TRANSACTION_ID.toString())
            assertThat(it["nomisTransactionEntrySequence"]).isEqualTo(repaymentTransaction.transactionEntrySequence.toString())
            assertThat(it["nomisTransactionAmount"]).isEqualTo("5.42")
            assertThat(it["nomisTransactionType"]).isEqualTo("DED")
            assertThat(it["offenderDeductionId"]).isEqualTo(deductionId.toString())
            assertThat(it["dpsAdvanceId"]).isEqualTo(dpsAdvanceId.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will not call the add or release hold transaction in DPS`() {
        financeApi.verify(0, postRequestedFor(urlPathEqualTo("/sync/holds")))
        financeApi.verify(0, postRequestedFor(urlPathMatching("/sync/holds/.*/release")))
      }
    }

    @Nested
    @DisplayName("When the transaction mapping already exists")
    inner class TransactionAlreadyMapped {
      @BeforeEach
      fun setUp() {
        financeMappingApiMockServer.stubGetByNomisId(NOMIS_TRANSACTION_ID)
        financeApi.stubPostPrisonerTransaction(receipt.copy(action = SyncTransactionReceipt.Action.PROCESSED))
        sendRepaymentEvent()
        waitForAnyProcessingToComplete("transactions-synchronisation-created-success-additional", "finance-advances-synchronisation-updated-success")
      }

      @Test
      fun `will repay the advance without creating another mapping`() {
        verifyRepayment()
        financeMappingApiMockServer.verify(0, postRequestedFor(urlPathEqualTo("/mapping/transactions")))
      }
    }

    @Nested
    @DisplayName("When transaction mapping creation detects a duplicate")
    inner class DuplicateTransactionMapping {
      val duplicateMapping = TransactionMappingDto(
        nomisBookingId = BOOKING_ID,
        dpsTransactionId = DPS_TRANSACTION_ID,
        nomisTransactionId = NOMIS_TRANSACTION_ID,
        offenderNo = OFFENDER_ID_DISPLAY,
        mappingType = TransactionMappingDto.MappingType.NOMIS_CREATED,
      )
      val existingMapping = duplicateMapping.copy(dpsTransactionId = UUID.randomUUID().toString())

      @BeforeEach
      fun setUp() {
        financeMappingApiMockServer.stubPostMapping(
          error = DuplicateErrorResponse(
            moreInfo = DuplicateErrorContent(
              duplicate = duplicateMapping,
              existing = existingMapping,
            ),
          ),
        )
        sendRepaymentEvent()
        waitForAnyProcessingToComplete("transaction-from-nomis-synch-duplicate", "finance-advances-synchronisation-updated-success")
      }

      @Test
      fun `will report the duplicate mapping and still repay the advance once`() {
        verify(telemetryClient).trackEvent(
          eq("transaction-from-nomis-synch-duplicate"),
          check {
            assertThat(it["duplicateDpsTransactionId"]).isEqualTo(DPS_TRANSACTION_ID)
            assertThat(it["existingDpsTransactionId"]).isEqualTo(existingMapping.dpsTransactionId)
            assertThat(it["duplicateNomisTransactionId"]).isEqualTo(NOMIS_TRANSACTION_ID.toString())
            assertThat(it["existingNomisTransactionId"]).isEqualTo(NOMIS_TRANSACTION_ID.toString())
          },
          isNull(),
        )
        financeMappingApiMockServer.verify(1, postRequestedFor(urlPathEqualTo("/mapping/transactions")))
        verifyRepayment()
      }
    }

    @Nested
    @DisplayName("When transaction mapping creation fails")
    inner class TransactionMappingFailure {
      @Nested
      @DisplayName("Fails once")
      inner class FailsOnce {
        @BeforeEach
        fun setUp() {
          financeMappingApiMockServer.stubPostMappingFailureFollowedBySuccess()
          sendRepaymentEvent()
          waitForAnyProcessingToComplete("finance-advances-synchronisation-updated-success", "transactions-mapping-created-synchronisation-success")
        }

        @Test
        fun `will retry the mapping without repaying the advance twice`() {
          financeMappingApiMockServer.verify(exactly(2), postRequestedFor(urlPathEqualTo("/mapping/transactions")))
          verifyRepayment()
        }
      }

      @Nested
      @DisplayName("Fails constantly")
      inner class FailsConstantly {
        @BeforeEach
        fun setUp() {
          financeMappingApiMockServer.stubPostMapping(status = HttpStatus.INTERNAL_SERVER_ERROR)
          sendRepaymentEvent()
          await untilCallTo {
            awsSqsFinanceOffenderEventsDlqClient.countAllMessagesOnQueue(financeQueueOffenderEventsDlqUrl).get()
          } matches { it == 1 }
        }

        @Test
        fun `will still repay the advance when mapping retries fail`() {
          financeMappingApiMockServer.verify(exactly(3), postRequestedFor(urlPathEqualTo("/mapping/transactions")))
          verifyRepayment()
        }
      }
    }

    @Nested
    @DisplayName("When the advance mapping is missing")
    inner class AdvanceMappingMissing {
      @BeforeEach
      fun setUp() {
        advancesMappingApiMockServer.stubGetByNomisId(advance.id, mapping = null)
        sendRepaymentEvent()
      }

      @Test
      fun `will fail synchronisation without repaying or creating an advance mapping`() {
        verifyRepaymentFailure("/mapping/advances/nomis-id/${advance.id}")
        financeApi.verify(0, postRequestedFor(urlPathMatching("/sync/advances/.*/repay")))
        advancesMappingApiMockServer.verify(0, postRequestedFor(urlPathEqualTo("/mapping/advances")))
      }
    }

    @Nested
    @DisplayName("When the advance mapping lookup fails")
    inner class AdvanceMappingFailure {
      @BeforeEach
      fun setUp() {
        advancesMappingApiMockServer.stubGetByNomisIdFailure(advance.id)
        sendRepaymentEvent()
      }

      @Test
      fun `will retry and fail synchronisation without repaying the advance`() {
        verifyRepaymentFailure("/mapping/advances/nomis-id/${advance.id}")
        advancesMappingApiMockServer.verify(
          exactly(2),
          getRequestedFor(urlPathEqualTo("/mapping/advances/nomis-id/${advance.id}")),
        )
        financeApi.verify(0, postRequestedFor(urlPathMatching("/sync/advances/.*/repay")))
      }
    }

    @Nested
    @DisplayName("When the advance is missing in NOMIS")
    inner class AdvanceMissing {
      @BeforeEach
      fun setUp() {
        advancesNomisApiMockServer.stubGetAdvanceByDeductionId(deductionId, status = HttpStatus.NOT_FOUND)
        sendRepaymentEvent()
      }

      @Test
      fun `will fail synchronisation without looking up the mapping or repaying the advance`() {
        verifyRepaymentFailure("/finance/advances/deduction-id/$deductionId")
        advancesMappingApiMockServer.verify(0, getRequestedFor(urlPathEqualTo("/mapping/advances/nomis-id/${advance.id}")))
        financeApi.verify(0, postRequestedFor(urlPathMatching("/sync/advances/.*/repay")))
      }
    }

    @Nested
    @DisplayName("When the DPS repayment fails")
    inner class RepaymentFailure {
      @BeforeEach
      fun setUp() {
        financeApi.stubFor(post("/sync/advances/$dpsAdvanceId/repay").willReturn(serverError()))
        sendRepaymentEvent()
      }

      @Test
      fun `will retry repayment and track failure`() {
        verifyRepaymentFailure("/sync/advances/$dpsAdvanceId/repay")
        financeApi.verify(2, postRequestedFor(urlPathEqualTo("/sync/advances/$dpsAdvanceId/repay")))
      }
    }

    @Nested
    @DisplayName("When advance processing is disabled")
    @TestPropertySource(properties = ["offender_transactions.process.advances=false"])
    inner class AdvanceProcessingDisabled {
      @BeforeEach
      fun setUp() {
        sendRepaymentEvent()
        waitForAnyProcessingToComplete("transactions-synchronisation-created-success")
      }

      @Test
      fun `will synchronise the transaction without looking up or repaying the advance`() {
        financeApi.verify(1, postRequestedFor(urlPathEqualTo("/sync/offender-transactions")))
        financeMappingApiMockServer.verify(1, postRequestedFor(urlPathEqualTo("/mapping/transactions")))
        advancesNomisApiMockServer.verify(0, getRequestedFor(urlPathEqualTo("/finance/advances/deduction-id/$deductionId")))
        advancesMappingApiMockServer.verify(0, getRequestedFor(urlPathEqualTo("/mapping/advances/nomis-id/${advance.id}")))
        financeApi.verify(0, postRequestedFor(urlPathMatching("/sync/advances/.*/repay")))
      }
    }

    @Nested
    @DisplayName("When the transaction is not a deduction")
    inner class NotADeduction {
      @BeforeEach
      fun setUp() {
        financeNomisApiMockServer.stubGetPrisonerTransaction(
          bookingId = BOOKING_ID,
          transactionId = NOMIS_TRANSACTION_ID,
          response = listOf(repaymentTransaction.copy(type = "type")),
        )
        sendRepaymentEvent()
        waitForAnyProcessingToComplete("transactions-synchronisation-created-success")
      }

      @Test
      fun `will synchronise the transaction without repaying the advance`() {
        financeApi.verify(1, postRequestedFor(urlPathEqualTo("/sync/offender-transactions")))
        advancesNomisApiMockServer.verify(0, getRequestedFor(urlPathEqualTo("/finance/advances/deduction-id/$deductionId")))
        financeApi.verify(0, postRequestedFor(urlPathMatching("/sync/advances/.*/repay")))
      }
    }

    @Nested
    @DisplayName("When the deduction amount is zero")
    inner class ZeroAmount {
      @BeforeEach
      fun setUp() {
        financeNomisApiMockServer.stubGetPrisonerTransaction(
          bookingId = BOOKING_ID,
          transactionId = NOMIS_TRANSACTION_ID,
          response = listOf(repaymentTransaction.copy(amount = BigDecimal.ZERO)),
        )
        sendRepaymentEvent()
        waitForAnyProcessingToComplete("transactions-synchronisation-created-success")
      }

      @Test
      fun `will synchronise the transaction without repaying the advance`() {
        financeApi.verify(1, postRequestedFor(urlPathEqualTo("/sync/offender-transactions")))
        advancesNomisApiMockServer.verify(0, getRequestedFor(urlPathEqualTo("/finance/advances/deduction-id/$deductionId")))
        financeApi.verify(0, postRequestedFor(urlPathMatching("/sync/advances/.*/repay")))
      }
    }

    fun sendRepaymentEvent() {
      financeOffenderEventsQueue.sendMessage(
        offenderTransactionEvent(
          messageId = messageUuid,
          bookingId = BOOKING_ID,
          transactionId = NOMIS_TRANSACTION_ID,
          offenderNo = OFFENDER_ID_DISPLAY,
        ),
      )
    }

    fun verifyRepayment() {
      financeApi.verify(
        1,
        postRequestedFor(urlPathEqualTo("/sync/advances/$dpsAdvanceId/repay"))
          .withRequestBodyJsonPath("transactionId", NOMIS_TRANSACTION_ID)
          .withRequestBodyJsonPath("entrySequence", repaymentTransaction.transactionEntrySequence)
          .withRequestBodyJsonPath("amount", repaymentTransaction.amount)
          .withRequestBodyJsonPath("caseloadId", repaymentTransaction.caseloadId)
          .withRequestBodyJsonPath("description", repaymentTransaction.description)
          .withRequestBodyJsonPath("createdBy", repaymentTransaction.createdBy)
          .withRequestBodyJsonPath("createdAt", repaymentTransaction.generalLedgerTransactions.first().transactionTimestamp),
      )
    }

    fun verifyRepaymentFailure(errorPath: String) {
      await untilCallTo {
        awsSqsFinanceOffenderEventsDlqClient.countAllMessagesOnQueue(financeQueueOffenderEventsDlqUrl).get()
      } matches { it == 1 }
      verify(telemetryClient, atLeastOnce()).trackEvent(
        eq("finance-advances-synchronisation-updated-error"),
        check {
          assertThat(it["prisonNumber"]).isEqualTo(OFFENDER_ID_DISPLAY)
          assertThat(it["nomisTransactionId"]).isEqualTo(NOMIS_TRANSACTION_ID.toString())
          assertThat(it["offenderDeductionId"]).isEqualTo(deductionId.toString())
          assertThat(it["error"]).endsWith(errorPath)
        },
        isNull(),
      )
      verify(telemetryClient, atLeastOnce()).trackEvent(
        eq("transactions-synchronisation-created-failed"),
        check {
          assertThat(it["nomisTransactionId"]).isEqualTo(NOMIS_TRANSACTION_ID.toString())
          assertThat(it["error"]).endsWith(errorPath)
        },
        isNull(),
      )
    }
  }

  @Nested
  @DisplayName("Write off advance transaction")
  inner class WriteOffAdvance {
    // TODO add write off tests
  }

  private fun Any.toJson(): String = jsonMapper.writeValueAsString(this)

  private fun offenderTransactionEvent(
    messageId: UUID,
    bookingId: Long = BOOKING_ID,
    transactionId: Long = NOMIS_TRANSACTION_ID,
    offenderNo: String = OFFENDER_ID_DISPLAY,
    auditModuleName: String = "OTIDTACC",
  ) = SQSMessage(
    MessageId = "$messageId",
    Type = "Notification",
    Message = TransactionEvent(
      transactionId = transactionId,
      entrySequence = 1,
      caseload = "SWI",
      offenderIdDisplay = offenderNo,
      bookingId = bookingId,
      transactionType = "DED",
      auditModuleName = auditModuleName,
    ).toJson(),
    MessageAttributes = MessageAttributes(EventType("OFFENDER_TRANSACTIONS-INSERTED", "String")),
  ).toJson()
}
