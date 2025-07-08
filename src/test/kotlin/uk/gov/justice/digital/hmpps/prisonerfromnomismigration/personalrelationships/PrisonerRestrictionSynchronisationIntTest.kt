package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships

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

class PrisonerRestrictionSynchronisationIntTest : SqsIntegrationTestBase() {

  @Nested
  @DisplayName("RESTRICTION-UPSERTED created")
  inner class PrisonerRestrictionCreated {
    private val offenderRestrictionId = 3456L
    private val offenderNo = "A1234KT"

    @Nested
    inner class WhenCreatedInDps {
      @BeforeEach
      fun setUp() {
        personalRelationshipsOffenderEventsQueue.sendMessage(
          prisonerRestrictionEvent(
            eventType = "RESTRICTION-UPSERTED",
            isUpdated = false,
            offenderNo = offenderNo,
            restrictionId = offenderRestrictionId,
            auditModuleName = "DPS_SYNCHRONISATION",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-prisoner-restriction-synchronisation-created-skipped"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(offenderNo)
            assertThat(it["nomisRestrictionId"]).isEqualTo(offenderRestrictionId.toString())
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenCreatedInNomis {
      @BeforeEach
      fun setUp() {
        personalRelationshipsOffenderEventsQueue.sendMessage(
          prisonerRestrictionEvent(
            eventType = "RESTRICTION-UPSERTED",
            isUpdated = false,
            offenderNo = offenderNo,
            restrictionId = offenderRestrictionId,
            auditModuleName = "OCUMINN",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-prisoner-restriction-synchronisation-created-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(offenderNo)
            assertThat(it["nomisRestrictionId"]).isEqualTo(offenderRestrictionId.toString())
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("RESTRICTION-UPSERTED updated")
  inner class PrisonerRestrictionUpdated {
    private val offenderRestrictionId = 3456L
    private val offenderNo = "A1234KT"

    @Nested
    inner class WhenUpdatedInDps {
      @BeforeEach
      fun setUp() {
        personalRelationshipsOffenderEventsQueue.sendMessage(
          prisonerRestrictionEvent(
            eventType = "RESTRICTION-UPSERTED",
            isUpdated = true,
            offenderNo = offenderNo,
            restrictionId = offenderRestrictionId,
            auditModuleName = "DPS_SYNCHRONISATION",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-prisoner-restriction-synchronisation-updated-skipped"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(offenderNo)
            assertThat(it["nomisRestrictionId"]).isEqualTo(offenderRestrictionId.toString())
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenUpdatedInNomis {
      @BeforeEach
      fun setUp() {
        personalRelationshipsOffenderEventsQueue.sendMessage(
          prisonerRestrictionEvent(
            eventType = "RESTRICTION-UPSERTED",
            isUpdated = true,
            offenderNo = offenderNo,
            restrictionId = offenderRestrictionId,
            auditModuleName = "OCUMINN",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-prisoner-restriction-synchronisation-updated-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(offenderNo)
            assertThat(it["nomisRestrictionId"]).isEqualTo(offenderRestrictionId.toString())
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("RESTRICTION-DELETED")
  inner class PrisonerRestrictionDeleted {
    private val offenderRestrictionId = 3456L
    private val offenderNo = "A1234KT"

    @Nested
    inner class WhenDeletedInDps {
      @BeforeEach
      fun setUp() {
        personalRelationshipsOffenderEventsQueue.sendMessage(
          prisonerRestrictionEvent(
            eventType = "RESTRICTION-DELETED",
            isUpdated = true,
            offenderNo = offenderNo,
            restrictionId = offenderRestrictionId,
            auditModuleName = "DPS_SYNCHRONISATION",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-prisoner-restriction-synchronisation-deleted-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(offenderNo)
            assertThat(it["nomisRestrictionId"]).isEqualTo(offenderRestrictionId.toString())
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenDeletedInNomis {
      @BeforeEach
      fun setUp() {
        personalRelationshipsOffenderEventsQueue.sendMessage(
          prisonerRestrictionEvent(
            eventType = "RESTRICTION-DELETED",
            isUpdated = true,
            offenderNo = offenderNo,
            restrictionId = offenderRestrictionId,
            auditModuleName = "OCUMINN",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-prisoner-restriction-synchronisation-deleted-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(offenderNo)
            assertThat(it["nomisRestrictionId"]).isEqualTo(offenderRestrictionId.toString())
          },
          isNull(),
        )
      }
    }
  }
}

fun prisonerRestrictionEvent(
  eventType: String,
  restrictionId: Long,
  offenderNo: String,
  isUpdated: Boolean,
  auditModuleName: String = "OMUVREST",
) = // language=JSON
  """{
    "MessageId": "ae06c49e-1f41-4b9f-b2f2-dcca610d02cd", "Type": "Notification", "Timestamp": "2019-10-21T14:01:18.500Z", 
    "Message": "{\"eventId\":\"5958295\",\"eventType\":\"$eventType\",\"eventDatetime\":\"2019-10-21T15:00:25.489964\",\"offenderIdDisplay\": \"$offenderNo\", \"isUpdated\": $isUpdated, \"offenderRestrictionId\": \"$restrictionId\",\"auditModuleName\":\"$auditModuleName\",\"restrictionType\": \"BAN\",\"effectiveDate\": \"2021-10-15\",\"expiryDate\": \"2022-01-13\",\"enteredById\": \"485887\",\"nomisEventType\":\"OFF_RESTRICTS-UPDATED\" }",
    "TopicArn": "arn:aws:sns:eu-west-1:000000000000:offender_events", 
    "MessageAttributes": {
      "eventType": {"Type": "String", "Value": "$eventType"}, 
      "id": {"Type": "String", "Value": "8b07cbd9-0820-0a0f-c32f-a9429b618e0b"}, 
      "contentType": {"Type": "String", "Value": "text/plain;charset=UTF-8"}, 
      "timestamp": {"Type": "Number.java.lang.Long", "Value": "1571666478344"}
    }
}
  """.trimIndent()
