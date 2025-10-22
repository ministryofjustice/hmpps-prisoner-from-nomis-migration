package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits

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

class OfficialVisitsSynchronisationIntTest : SqsIntegrationTestBase() {
  val offenderNo = "A1234KT"
  val bookingId = 1234L
  val prisonId = "MDI"
  val nomisVisitId = 65432L

  @Nested
  @DisplayName("OFFENDER_OFFICIAL_VISIT-INSERTED")
  inner class OfficialVisitCreated {

    @Nested
    inner class WhenCreatedInNomis {
      @BeforeEach
      fun setUp() {
        officialVisitsOffenderEventsQueue.sendMessage(
          officialVisitEvent(
            eventType = "OFFENDER_OFFICIAL_VISIT-INSERTED",
            offenderNo = offenderNo,
            visitId = nomisVisitId,
            agencyLocationId = prisonId,
            bookingId = bookingId,
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("officialvisits-visit-synchronisation-created-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(offenderNo)
            assertThat(it["nomisVisitId"]).isEqualTo(nomisVisitId.toString())
            assertThat(it["bookingId"]).isEqualTo(bookingId.toString())
            assertThat(it["prisonId"]).isEqualTo(prisonId)
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("OFFENDER_OFFICIAL_VISIT-UPDATED")
  inner class OfficialVisitUpdated {

    @Nested
    inner class WhenUpdatedInNomis {
      @BeforeEach
      fun setUp() {
        officialVisitsOffenderEventsQueue.sendMessage(
          officialVisitEvent(
            eventType = "OFFENDER_OFFICIAL_VISIT-UPDATED",
            offenderNo = offenderNo,
            visitId = nomisVisitId,
            agencyLocationId = prisonId,
            bookingId = bookingId,
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("officialvisits-visit-synchronisation-updated-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(offenderNo)
            assertThat(it["nomisVisitId"]).isEqualTo(nomisVisitId.toString())
            assertThat(it["bookingId"]).isEqualTo(bookingId.toString())
            assertThat(it["prisonId"]).isEqualTo(prisonId)
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("OFFENDER_OFFICIAL_VISIT-DELETED")
  inner class OfficialVisitDeleted {

    @Nested
    inner class WhenDeletedInNomis {
      @BeforeEach
      fun setUp() {
        officialVisitsOffenderEventsQueue.sendMessage(
          officialVisitEvent(
            eventType = "OFFENDER_OFFICIAL_VISIT-DELETED",
            offenderNo = offenderNo,
            visitId = nomisVisitId,
            agencyLocationId = prisonId,
            bookingId = bookingId,
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("officialvisits-visit-synchronisation-deleted-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(offenderNo)
            assertThat(it["nomisVisitId"]).isEqualTo(nomisVisitId.toString())
            assertThat(it["bookingId"]).isEqualTo(bookingId.toString())
            assertThat(it["prisonId"]).isEqualTo(prisonId)
          },
          isNull(),
        )
      }
    }
  }
}

fun officialVisitEvent(
  eventType: String,
  visitId: Long,
  offenderNo: String,
  agencyLocationId: String,
  bookingId: Long,
  auditModuleName: String = "OIDUVISI",
) = // language=JSON
  """{
    "MessageId": "ae06c49e-1f41-4b9f-b2f2-dcca610d02cd", "Type": "Notification", "Timestamp": "2019-10-21T14:01:18.500Z", 
    "Message": "{\"eventType\":\"$eventType\",\"eventDatetime\":\"2019-10-21T15:00:25.489964\",\"offenderIdDisplay\": \"$offenderNo\", \"bookingId\": $bookingId, \"visitId\": $visitId,\"auditModuleName\":\"$auditModuleName\",\"agencyLocationId\": \"$agencyLocationId\",\"nomisEventType\":\"$eventType\" }",
    "TopicArn": "arn:aws:sns:eu-west-1:000000000000:offender_events", 
    "MessageAttributes": {
      "eventType": {"Type": "String", "Value": "$eventType"}, 
      "id": {"Type": "String", "Value": "8b07cbd9-0820-0a0f-c32f-a9429b618e0b"}, 
      "contentType": {"Type": "String", "Value": "text/plain;charset=UTF-8"}, 
      "timestamp": {"Type": "Number.java.lang.Long", "Value": "1571666478344"}
    }
}
  """.trimIndent()
