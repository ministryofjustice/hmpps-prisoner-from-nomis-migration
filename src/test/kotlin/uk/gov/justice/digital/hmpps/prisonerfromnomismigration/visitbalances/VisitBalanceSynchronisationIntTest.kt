package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visitbalances

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito.eq
import org.mockito.kotlin.check
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage

class VisitBalanceSynchronisationIntTest : SqsIntegrationTestBase() {

  @Nested
  @DisplayName("OFFENDER_VISIT_BALANCE_ADJS-INSERTED")
  inner class VisitBalanceAdjustmentInserted {
    private val visitBalanceAdjId = 123456L
    private val nomisPrisonNumber = "A1234BC"

    @Nested
    inner class WhenCreatedInDps {
      @BeforeEach
      fun setUp() {
        visitBalanceOffenderEventsQueue.sendMessage(
          visitBalanceAdjustmentEvent(
            eventType = "OFFENDER_VISIT_BALANCE_ADJS-INSERTED",
            visitBalanceAdjId = visitBalanceAdjId,
            auditModuleName = "DPS_SYNCHRONISATION",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("visitbalance-adjustment-synchronisation-inserted-notimplemented"),
          check {
            assertThat(it["visitBalanceAdjustmentId"]).isEqualTo(visitBalanceAdjId.toString())
            assertThat(it["nomisPrisonNumber"]).isEqualTo(nomisPrisonNumber.toString())
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenCreatedInNomis {
      @BeforeEach
      fun setUp() {
        visitBalanceOffenderEventsQueue.sendMessage(
          visitBalanceAdjustmentEvent(
            eventType = "OFFENDER_VISIT_BALANCE_ADJS-INSERTED",
            visitBalanceAdjId = visitBalanceAdjId,
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("visitbalance-adjustment-synchronisation-inserted-notimplemented"),
          check {
            assertThat(it["visitBalanceAdjustmentId"]).isEqualTo(visitBalanceAdjId.toString())
            assertThat(it["nomisPrisonNumber"]).isEqualTo(nomisPrisonNumber.toString())
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("OFFENDER_VISIT_BALANCE_ADJS-UPDATED")
  inner class VisitBalanceAdjustmentUpdated {
    private val visitBalanceAdjId = 123456L
    private val nomisPrisonNumber = "A1234BC"

    @Nested
    inner class WhenUpdatedInDps {
      @BeforeEach
      fun setUp() {
        visitBalanceOffenderEventsQueue.sendMessage(
          visitBalanceAdjustmentEvent(
            eventType = "OFFENDER_VISIT_BALANCE_ADJS-UPDATED",
            visitBalanceAdjId = visitBalanceAdjId,
            auditModuleName = "DPS_SYNCHRONISATION",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("visitbalance-adjustment-synchronisation-updated-notimplemented"),
          check {
            assertThat(it["visitBalanceAdjustmentId"]).isEqualTo(visitBalanceAdjId.toString())
            assertThat(it["nomisPrisonNumber"]).isEqualTo(nomisPrisonNumber.toString())
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenUpdatedInNomis {
      private val nomisVisitBalanceAdjId = 123456L
      private val nomisPrisonNumber = "A1234BC"

      @BeforeEach
      fun setUp() {
        visitBalanceOffenderEventsQueue.sendMessage(
          visitBalanceAdjustmentEvent(
            eventType = "OFFENDER_VISIT_BALANCE_ADJS-UPDATED",
            visitBalanceAdjId = nomisVisitBalanceAdjId,
            auditModuleName = "NOMIS_SYNCHRONISATION",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("visitbalance-adjustment-synchronisation-updated-notimplemented"),
          check {
            assertThat(it["visitBalanceAdjustmentId"]).isEqualTo(nomisVisitBalanceAdjId.toString())
            assertThat(it["nomisPrisonNumber"]).isEqualTo(nomisPrisonNumber.toString())
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("OFFENDER_VISIT_BALANCE_ADJS-DELETED")
  inner class VisitBalanceAdjustmentDeleted {
    private val nomisVisitBalanceAdjId = 123456L
    private val nomisPrisonNumber = "A1234BC"

    @Nested
    inner class WhenDeletedInDps {
      @BeforeEach
      fun setUp() {
        visitBalanceOffenderEventsQueue.sendMessage(
          visitBalanceAdjustmentEvent(
            eventType = "OFFENDER_VISIT_BALANCE_ADJS-DELETED",
            visitBalanceAdjId = nomisVisitBalanceAdjId,
            auditModuleName = "DPS_SYNCHRONISATION",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("visitbalance-adjustment-synchronisation-deleted-notimplemented"),
          check {
            assertThat(it["visitBalanceAdjustmentId"]).isEqualTo(nomisVisitBalanceAdjId.toString())
            assertThat(it["nomisPrisonNumber"]).isEqualTo(nomisPrisonNumber.toString())
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenDeletedInNomis {
      private val nomisVisitBalanceAdjId = 123456L
      private val nomisPrisonNumber = "A1234BC"

      @BeforeEach
      fun setUp() {
        visitBalanceOffenderEventsQueue.sendMessage(
          visitBalanceAdjustmentEvent(
            eventType = "OFFENDER_VISIT_BALANCE_ADJS-DELETED",
            visitBalanceAdjId = nomisVisitBalanceAdjId,
            auditModuleName = "NOMIS_SYNCHRONISATION",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("visitbalance-adjustment-synchronisation-deleted-notimplemented"),
          check {
            assertThat(it["visitBalanceAdjustmentId"]).isEqualTo(nomisVisitBalanceAdjId.toString())
            assertThat(it["nomisPrisonNumber"]).isEqualTo(nomisPrisonNumber.toString())
          },
          isNull(),
        )
      }
    }
  }
}

fun visitBalanceAdjustmentEvent(
  eventType: String = "OFFENDER_VISIT_BALANCE_ADJS-INSERTED",
  visitBalanceAdjId: Long,
  auditModuleName: String = "OIDVIORD",
) = // language=JSON
  """{
    "MessageId": "ae06c49e-1f41-4b9f-b2f2-dcca610d02cd", "Type": "Notification", "Timestamp": "2019-10-21T14:01:18.500Z", 
    "Message": "{\"eventId\":\"5958295\",\"eventType\":\"$eventType\",\"eventDatetime\":\"2019-10-21T15:00:25.489964\",\"bookingId\":1215724,\"visitBalanceAdjustmentId\": \"$visitBalanceAdjId\",\"offenderIdDisplay\":\"A1234BC\",\"offenderId\":2581911,\"rootOffenderId\":2581911,\"auditModuleName\":\"$auditModuleName\",\"nomisEventType\":\"$eventType\" }",
    "TopicArn": "arn:aws:sns:eu-west-1:000000000000:offender_events", 
    "MessageAttributes": {
      "eventType": {"Type": "String", "Value": "$eventType"}, 
      "id": {"Type": "String", "Value": "8b07cbd9-0820-0a0f-c32f-a9429b618e0b"}, 
      "contentType": {"Type": "String", "Value": "text/plain;charset=UTF-8"}, 
      "timestamp": {"Type": "Number.java.lang.Long", "Value": "1571666478344"}
    }
}
  """.trimIndent()
