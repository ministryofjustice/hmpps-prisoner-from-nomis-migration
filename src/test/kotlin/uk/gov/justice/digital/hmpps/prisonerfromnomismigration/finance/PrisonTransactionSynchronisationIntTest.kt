package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance

import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
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
import org.mockito.ArgumentMatchers.eq
import org.mockito.kotlin.check
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.FinanceApiExtension.Companion.financeApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.model.SyncTransactionReceipt
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.EventType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MessageAttributes
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.SQSMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TransactionMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.GeneralLedgerTransactionDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.TransactionIdBufferRepository
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.AbstractMap.SimpleEntry
import java.util.UUID

class PrisonTransactionSynchronisationIntTest : SqsIntegrationTestBase() {

  companion object {
    val dpsTransactionUuid: UUID = UUID.fromString(DPS_TRANSACTION_ID)
    val messageUuid: UUID = UUID.fromString(MESSAGE_ID)
    internal const val BOOKING_ID = 1234L
    internal const val NOMIS_TRANSACTION_ID = 2345678L
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
  @DisplayName("GL_TRANSACTIONS-INSERTED")
  inner class GeneralLedgerTransactionInserted {

    @BeforeEach
    fun setUp() = runTest {
      transactionIdBufferRepository.deleteAll()
    }

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

          financeOffenderEventsQueue.sendMessage(
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

          financeOffenderEventsQueue.sendMessage(
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

  @Nested
  @DisplayName("GL_TRANSACTIONS-UPDATED")
  inner class GeneralLedgerTransactionUpdated {
    // TODO
  }

  private fun Any.toJson(): String = jsonMapper.writeValueAsString(this)

  fun glTransactionEvent(
    eventType: String,
    messageId: UUID,
    bookingId: Long = BOOKING_ID,
    transactionId: Long = NOMIS_TRANSACTION_ID,
    offenderNo: String = OFFENDER_ID_DISPLAY,
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
      auditModuleName = "PRISON_API",
    ).toJson(),
    MessageAttributes = MessageAttributes(EventType(eventType, "String")),
  ).toJson()

  fun nomisGLTransactions(transactionId: Long = NOMIS_TRANSACTION_ID) = listOf(
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
}
