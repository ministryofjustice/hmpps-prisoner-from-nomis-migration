package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.atLeast
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.FinanceApiExtension.Companion.financeApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.TransactionSynchronisationIntTest.Companion.dpsTransactionUuid
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.TransactionSynchronisationIntTest.Companion.messageUuid
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.model.SyncTransactionReceipt
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.EventType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MessageAttributes
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.SQSMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TransactionMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.TransactionIdBufferRepository
import java.time.Duration
import java.util.UUID

@TestPropertySource(properties = ["finance.transactions.forwardingDelaySeconds=1"])
class TransactionSynchronisationMultiIntTest : SqsIntegrationTestBase() {

  @Autowired
  private lateinit var financeNomisApiMockServer: FinanceNomisApiMockServer

  @Autowired
  private lateinit var objectMapper: ObjectMapper

  @Autowired
  private lateinit var financeMappingApiMockServer: FinanceMappingApiMockServer

  @Autowired
  private lateinit var transactionIdBufferRepository: TransactionIdBufferRepository

  @BeforeEach
  fun init() = runTest {
    transactionIdBufferRepository.deleteAll()
  }

  @Nested
  @DisplayName("Happy path where there are multiple concurrent events")
  inner class HappyPathMultipleEvents {

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

      val sendMessageRequest = SendMessageRequest.builder()
        .queueUrl(financeQueueOffenderEventsUrl)
        .messageBody(
          offenderTransactionEvent("OFFENDER_TRANSACTIONS-INSERTED", messageUuid),
        ).build()
      val m1 = awsSqsFinanceOffenderEventsClient.sendMessage(sendMessageRequest)
      val m2 = awsSqsFinanceOffenderEventsClient.sendMessage(sendMessageRequest)
      val m3 = awsSqsFinanceOffenderEventsClient.sendMessage(sendMessageRequest)
      val m4 = awsSqsFinanceOffenderEventsClient.sendMessage(sendMessageRequest)
      m1.get()
      m2.get()
      m3.get()
      m4.get()
    }

    @Test
    fun `only one call made to DPS and transaction id is removed from DB afterwards`() {
      await atLeast Duration.ofSeconds(1) untilAsserted {
        financeApi.verify(
          1,
          postRequestedFor(urlPathEqualTo("/sync/offender-transactions")),
        )
      }
      await untilAsserted {
        val result = runBlocking { transactionIdBufferRepository.existsById(NOMIS_TRANSACTION_ID) }
        assertThat(result).isFalse
      }
    }
  }

  private fun Any.toJson(): String = objectMapper.writeValueAsString(this)

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
