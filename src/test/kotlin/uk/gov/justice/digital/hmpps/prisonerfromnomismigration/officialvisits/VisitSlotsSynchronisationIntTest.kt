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

class VisitSlotsSynchronisationIntTest : SqsIntegrationTestBase() {
  val nomisTimeslotSequence = 2
  val nomisWeekDay = "MON"
  val prisonId = "MDI"
  val nomisAgencyVisitSlotId = 12345L

  @Nested
  @DisplayName("AGENCY_VISIT_TIMES-INSERTED")
  inner class AgencyVisitTimeCreated {

    @Nested
    inner class WhenCreatedInNomis {
      @BeforeEach
      fun setUp() {
        officialVisitsOffenderEventsQueue.sendMessage(
          agencyVisitTimeEvent(
            eventType = "AGENCY_VISIT_TIMES-INSERTED",
            agencyLocationId = prisonId,
            timeslotSequence = nomisTimeslotSequence,
            weekDay = nomisWeekDay,
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("officialvisits-timeslot-synchronisation-created-success"),
          check {
            assertThat(it["prisonId"]).isEqualTo(prisonId)
            assertThat(it["nomisWeekDay"]).isEqualTo(nomisWeekDay)
            assertThat(it["nomisTimeslotSequence"]).isEqualTo(nomisTimeslotSequence.toString())
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("AGENCY_VISIT_TIMES-UPDATED")
  inner class AgencyVisitTimeUpdated {

    @Nested
    inner class WhenUpdatedInNomis {
      @BeforeEach
      fun setUp() {
        officialVisitsOffenderEventsQueue.sendMessage(
          agencyVisitTimeEvent(
            eventType = "AGENCY_VISIT_TIMES-UPDATED",
            agencyLocationId = prisonId,
            timeslotSequence = nomisTimeslotSequence,
            weekDay = nomisWeekDay,
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("officialvisits-timeslot-synchronisation-updated-success"),
          check {
            assertThat(it["prisonId"]).isEqualTo(prisonId)
            assertThat(it["nomisWeekDay"]).isEqualTo(nomisWeekDay)
            assertThat(it["nomisTimeslotSequence"]).isEqualTo(nomisTimeslotSequence.toString())
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("AGENCY_VISIT_TIMES-DELETED")
  inner class AgencyVisitTimeDeleted {

    @Nested
    inner class WhenDeletedInNomis {
      @BeforeEach
      fun setUp() {
        officialVisitsOffenderEventsQueue.sendMessage(
          agencyVisitTimeEvent(
            eventType = "AGENCY_VISIT_TIMES-DELETED",
            agencyLocationId = prisonId,
            timeslotSequence = nomisTimeslotSequence,
            weekDay = nomisWeekDay,
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("officialvisits-timeslot-synchronisation-deleted-success"),
          check {
            assertThat(it["prisonId"]).isEqualTo(prisonId)
            assertThat(it["nomisWeekDay"]).isEqualTo(nomisWeekDay)
            assertThat(it["nomisTimeslotSequence"]).isEqualTo(nomisTimeslotSequence.toString())
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("AGENCY_VISIT_SLOTS-INSERTED")
  inner class AgencyVisitSlotCreated {

    @Nested
    inner class WhenCreatedInNomis {
      @BeforeEach
      fun setUp() {
        officialVisitsOffenderEventsQueue.sendMessage(
          agencyVisitSlotEvent(
            eventType = "AGENCY_VISIT_SLOTS-INSERTED",
            agencyVisitSlotId = nomisAgencyVisitSlotId,
            agencyLocationId = prisonId,
            timeslotSequence = nomisTimeslotSequence,
            weekDay = nomisWeekDay,
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("officialvisits-visitslot-synchronisation-created-success"),
          check {
            assertThat(it["prisonId"]).isEqualTo(prisonId)
            assertThat(it["nomisWeekDay"]).isEqualTo(nomisWeekDay)
            assertThat(it["nomisTimeslotSequence"]).isEqualTo(nomisTimeslotSequence.toString())
            assertThat(it["nomisAgencyVisitSlotId"]).isEqualTo(nomisAgencyVisitSlotId.toString())
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("AGENCY_VISIT_SLOTS-UPDATED")
  inner class AgencyVisitSlotUpdated {

    @Nested
    inner class WhenUpdatedInNomis {
      @BeforeEach
      fun setUp() {
        officialVisitsOffenderEventsQueue.sendMessage(
          agencyVisitSlotEvent(
            eventType = "AGENCY_VISIT_SLOTS-UPDATED",
            agencyVisitSlotId = nomisAgencyVisitSlotId,
            agencyLocationId = prisonId,
            timeslotSequence = nomisTimeslotSequence,
            weekDay = nomisWeekDay,
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("officialvisits-visitslot-synchronisation-updated-success"),
          check {
            assertThat(it["prisonId"]).isEqualTo(prisonId)
            assertThat(it["nomisWeekDay"]).isEqualTo(nomisWeekDay)
            assertThat(it["nomisTimeslotSequence"]).isEqualTo(nomisTimeslotSequence.toString())
            assertThat(it["nomisAgencyVisitSlotId"]).isEqualTo(nomisAgencyVisitSlotId.toString())
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("AGENCY_VISIT_SLOTS-DELETED")
  inner class AgencyVisitSlotDeleted {

    @Nested
    inner class WhenDeletedInNomis {
      @BeforeEach
      fun setUp() {
        officialVisitsOffenderEventsQueue.sendMessage(
          agencyVisitSlotEvent(
            eventType = "AGENCY_VISIT_SLOTS-DELETED",
            agencyVisitSlotId = nomisAgencyVisitSlotId,
            agencyLocationId = prisonId,
            timeslotSequence = nomisTimeslotSequence,
            weekDay = nomisWeekDay,
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("officialvisits-visitslot-synchronisation-deleted-success"),
          check {
            assertThat(it["prisonId"]).isEqualTo(prisonId)
            assertThat(it["nomisWeekDay"]).isEqualTo(nomisWeekDay)
            assertThat(it["nomisTimeslotSequence"]).isEqualTo(nomisTimeslotSequence.toString())
            assertThat(it["nomisAgencyVisitSlotId"]).isEqualTo(nomisAgencyVisitSlotId.toString())
          },
          isNull(),
        )
      }
    }
  }
}

fun agencyVisitSlotEvent(
  eventType: String,
  agencyVisitSlotId: Long,
  agencyLocationId: String,
  timeslotSequence: Int,
  weekDay: String,
  auditModuleName: String = "OIMVDTSL",
) = // language=JSON
  """{
    "MessageId": "ae06c49e-1f41-4b9f-b2f2-dcca610d02cd", "Type": "Notification", "Timestamp": "2019-10-21T14:01:18.500Z", 
    "Message": "{\"eventType\":\"$eventType\",\"eventDatetime\":\"2019-10-21T15:00:25.489964\", \"agencyVisitSlotId\": $agencyVisitSlotId,  \"timeslotSequence\": $timeslotSequence, \"weekDay\": \"$weekDay\",\"auditModuleName\":\"$auditModuleName\",\"agencyLocationId\": \"$agencyLocationId\",\"nomisEventType\":\"$eventType\" }",
    "TopicArn": "arn:aws:sns:eu-west-1:000000000000:offender_events", 
    "MessageAttributes": {
      "eventType": {"Type": "String", "Value": "$eventType"}, 
      "id": {"Type": "String", "Value": "8b07cbd9-0820-0a0f-c32f-a9429b618e0b"}, 
      "contentType": {"Type": "String", "Value": "text/plain;charset=UTF-8"}, 
      "timestamp": {"Type": "Number.java.lang.Long", "Value": "1571666478344"}
    }
}
  """.trimIndent()
fun agencyVisitTimeEvent(
  eventType: String,
  agencyLocationId: String,
  timeslotSequence: Int,
  weekDay: String,
  auditModuleName: String = "OIMVDTSL",
) = // language=JSON
  """{
    "MessageId": "ae06c49e-1f41-4b9f-b2f2-dcca610d02cd", "Type": "Notification", "Timestamp": "2019-10-21T14:01:18.500Z", 
    "Message": "{\"eventType\":\"$eventType\",\"eventDatetime\":\"2019-10-21T15:00:25.489964\", \"timeslotSequence\": $timeslotSequence, \"weekDay\": \"$weekDay\",\"auditModuleName\":\"$auditModuleName\",\"agencyLocationId\": \"$agencyLocationId\",\"nomisEventType\":\"$eventType\" }",
    "TopicArn": "arn:aws:sns:eu-west-1:000000000000:offender_events", 
    "MessageAttributes": {
      "eventType": {"Type": "String", "Value": "$eventType"}, 
      "id": {"Type": "String", "Value": "8b07cbd9-0820-0a0f-c32f-a9429b618e0b"}, 
      "contentType": {"Type": "String", "Value": "text/plain;charset=UTF-8"}, 
      "timestamp": {"Type": "Number.java.lang.Long", "Value": "1571666478344"}
    }
}
  """.trimIndent()
