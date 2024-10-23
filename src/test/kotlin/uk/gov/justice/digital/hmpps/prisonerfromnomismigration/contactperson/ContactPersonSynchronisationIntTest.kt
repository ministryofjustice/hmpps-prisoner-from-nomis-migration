package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson

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

class ContactPersonSynchronisationIntTest : SqsIntegrationTestBase() {
  @Nested
  @DisplayName("VISITOR_RESTRICTION-UPSERTED")
  inner class PersonRestrictionUpserted {
    private val restrictionId = 9876L
    private val personId = 123456L

    @BeforeEach
    fun setUp() {
      awsSqsContactPersonOffenderEventsClient.sendMessage(
        contactPersonQueueOffenderEventsUrl,
        personRestrictionEvent(
          eventType = "VISITOR_RESTRICTION-UPSERTED",
          personId = personId,
          restrictionId = restrictionId,
        ),
      ).also { waitForAnyProcessingToComplete() }
    }

    @Test
    fun `will track telemetry`() {
      verify(telemetryClient).trackEvent(
        eq("contactperson-person-restriction-synchronisation-created-success"),
        check {
          assertThat(it["personRestrictionId"]).isEqualTo(restrictionId.toString())
          assertThat(it["personId"]).isEqualTo(personId.toString())
        },
        isNull(),
      )
    }
  }

  @Nested
  @DisplayName("VISITOR_RESTRICTION-DELETED")
  inner class PersonRestrictionDeleted {
    private val restrictionId = 9876L
    private val personId = 123456L

    @BeforeEach
    fun setUp() {
      awsSqsContactPersonOffenderEventsClient.sendMessage(
        contactPersonQueueOffenderEventsUrl,
        personRestrictionEvent(
          eventType = "VISITOR_RESTRICTION-DELETED",
          personId = personId,
          restrictionId = restrictionId,
        ),
      ).also { waitForAnyProcessingToComplete() }
    }

    @Test
    fun `will track telemetry`() {
      verify(telemetryClient).trackEvent(
        eq("contactperson-person-restriction-synchronisation-deleted-success"),
        check {
          assertThat(it["personRestrictionId"]).isEqualTo(restrictionId.toString())
          assertThat(it["personId"]).isEqualTo(personId.toString())
        },
        isNull(),
      )
    }
  }

  @Nested
  @DisplayName("OFFENDER_CONTACT-INSERTED")
  inner class ContactAdded {
    private val contactId = 3456L
    private val personId = 123456L
    private val bookingId = 890L
    private val offenderNo = "A1234KT"

    @BeforeEach
    fun setUp() {
      awsSqsContactPersonOffenderEventsClient.sendMessage(
        contactPersonQueueOffenderEventsUrl,
        contactEvent(
          eventType = "OFFENDER_CONTACT-INSERTED",
          personId = personId,
          contactId = contactId,
          bookingId = bookingId,
          offenderNo = offenderNo,
        ),
      ).also { waitForAnyProcessingToComplete() }
    }

    @Test
    fun `will track telemetry`() {
      verify(telemetryClient).trackEvent(
        eq("contactperson-contact-synchronisation-created-success"),
        check {
          assertThat(it["offenderNo"]).isEqualTo(offenderNo)
          assertThat(it["bookingId"]).isEqualTo(bookingId.toString())
          assertThat(it["contactId"]).isEqualTo(contactId.toString())
          assertThat(it["personId"]).isEqualTo(personId.toString())
        },
        isNull(),
      )
    }
  }

  @Nested
  @DisplayName("OFFENDER_CONTACT-UPDATED")
  inner class ContactUpdated {
    private val contactId = 3456L
    private val personId = 123456L
    private val bookingId = 890L
    private val offenderNo = "A1234KT"

    @BeforeEach
    fun setUp() {
      awsSqsContactPersonOffenderEventsClient.sendMessage(
        contactPersonQueueOffenderEventsUrl,
        contactEvent(
          eventType = "OFFENDER_CONTACT-UPDATED",
          personId = personId,
          contactId = contactId,
          bookingId = bookingId,
          offenderNo = offenderNo,
        ),
      ).also { waitForAnyProcessingToComplete() }
    }

    @Test
    fun `will track telemetry`() {
      verify(telemetryClient).trackEvent(
        eq("contactperson-contact-synchronisation-updated-success"),
        check {
          assertThat(it["offenderNo"]).isEqualTo(offenderNo)
          assertThat(it["bookingId"]).isEqualTo(bookingId.toString())
          assertThat(it["contactId"]).isEqualTo(contactId.toString())
          assertThat(it["personId"]).isEqualTo(personId.toString())
        },
        isNull(),
      )
    }
  }

  @Nested
  @DisplayName("OFFENDER_CONTACT-DELETED")
  inner class ContactDeleted {
    private val contactId = 3456L
    private val personId = 123456L
    private val bookingId = 890L
    private val offenderNo = "A1234KT"

    @BeforeEach
    fun setUp() {
      awsSqsContactPersonOffenderEventsClient.sendMessage(
        contactPersonQueueOffenderEventsUrl,
        contactEvent(
          eventType = "OFFENDER_CONTACT-DELETED",
          personId = personId,
          contactId = contactId,
          bookingId = bookingId,
          offenderNo = offenderNo,
        ),
      ).also { waitForAnyProcessingToComplete() }
    }

    @Test
    fun `will track telemetry`() {
      verify(telemetryClient).trackEvent(
        eq("contactperson-contact-synchronisation-deleted-success"),
        check {
          assertThat(it["offenderNo"]).isEqualTo(offenderNo)
          assertThat(it["bookingId"]).isEqualTo(bookingId.toString())
          assertThat(it["contactId"]).isEqualTo(contactId.toString())
          assertThat(it["personId"]).isEqualTo(personId.toString())
        },
        isNull(),
      )
    }
  }

  @Nested
  @DisplayName("PERSON_RESTRICTION-UPSERTED")
  inner class ContactRestrictionUpserted {
    private val restrictionId = 9876L
    private val personId = 123456L
    private val contactId = 3456L
    private val offenderNo = "A1234KT"

    @BeforeEach
    fun setUp() {
      awsSqsContactPersonOffenderEventsClient.sendMessage(
        contactPersonQueueOffenderEventsUrl,
        contactRestrictionEvent(
          eventType = "PERSON_RESTRICTION-UPSERTED",
          personId = personId,
          restrictionId = restrictionId,
          contactId = contactId,
          offenderNo = offenderNo,
        ),
      ).also { waitForAnyProcessingToComplete() }
    }

    @Test
    fun `will track telemetry`() {
      verify(telemetryClient).trackEvent(
        eq("contactperson-contact-restriction-synchronisation-created-success"),
        check {
          assertThat(it["contactRestrictionId"]).isEqualTo(restrictionId.toString())
          assertThat(it["personId"]).isEqualTo(personId.toString())
          assertThat(it["contactId"]).isEqualTo(contactId.toString())
          assertThat(it["offenderNo"]).isEqualTo(offenderNo)
        },
        isNull(),
      )
    }
  }

  @Nested
  @DisplayName("PERSON_RESTRICTION-DELETED")
  inner class ContactRestrictionDeleted {
    private val restrictionId = 9876L
    private val personId = 123456L
    private val contactId = 3456L
    private val offenderNo = "A1234KT"

    @BeforeEach
    fun setUp() {
      awsSqsContactPersonOffenderEventsClient.sendMessage(
        contactPersonQueueOffenderEventsUrl,
        contactRestrictionEvent(
          eventType = "PERSON_RESTRICTION-DELETED",
          personId = personId,
          restrictionId = restrictionId,
          contactId = contactId,
          offenderNo = offenderNo,
        ),
      ).also { waitForAnyProcessingToComplete() }
    }

    @Test
    fun `will track telemetry`() {
      verify(telemetryClient).trackEvent(
        eq("contactperson-contact-restriction-synchronisation-deleted-success"),
        check {
          assertThat(it["contactRestrictionId"]).isEqualTo(restrictionId.toString())
          assertThat(it["personId"]).isEqualTo(personId.toString())
          assertThat(it["contactId"]).isEqualTo(contactId.toString())
          assertThat(it["offenderNo"]).isEqualTo(offenderNo)
        },
        isNull(),
      )
    }
  }
}

fun personRestrictionEvent(
  eventType: String,
  restrictionId: Long,
  personId: Long,
  auditModuleName: String = "OMUVREST",
) =
  // language=JSON
  """{
    "MessageId": "ae06c49e-1f41-4b9f-b2f2-dcca610d02cd", "Type": "Notification", "Timestamp": "2019-10-21T14:01:18.500Z", 
    "Message": "{\"eventId\":\"5958295\",\"eventType\":\"$eventType\",\"eventDatetime\":\"2019-10-21T15:00:25.489964\",\"personId\": \"$personId\",\"visitorRestrictionId\": \"$restrictionId\",\"auditModuleName\":\"$auditModuleName\",\"restrictionType\": \"BAN\",\"effectiveDate\": \"2021-10-15\",\"expiryDate\": \"2022-01-13\",\"enteredById\": \"485887\",\"nomisEventType\":\"VISITOR_RESTRICTS-UPDATED\" }",
    "TopicArn": "arn:aws:sns:eu-west-1:000000000000:offender_events", 
    "MessageAttributes": {
      "eventType": {"Type": "String", "Value": "$eventType"}, 
      "id": {"Type": "String", "Value": "8b07cbd9-0820-0a0f-c32f-a9429b618e0b"}, 
      "contentType": {"Type": "String", "Value": "text/plain;charset=UTF-8"}, 
      "timestamp": {"Type": "Number.java.lang.Long", "Value": "1571666478344"}
    }
}
  """.trimIndent()

fun contactEvent(
  eventType: String,
  contactId: Long,
  personId: Long,
  bookingId: Long,
  offenderNo: String,
  auditModuleName: String = "OCDPERSO",
) =
  // language=JSON
  """{
    "MessageId": "ae06c49e-1f41-4b9f-b2f2-dcca610d02cd", "Type": "Notification", "Timestamp": "2019-10-21T14:01:18.500Z", 
    "Message": "{\"eventId\":\"5958295\",\"eventType\":\"$eventType\",\"eventDatetime\":\"2019-10-21T15:00:25.489964\",\"bookingId\": \"$bookingId\",\"offenderIdDisplay\": \"$offenderNo\",\"personId\": \"$personId\",\"contactId\": \"$contactId\",\"auditModuleName\":\"$auditModuleName\",\"approvedVisitor\": \"false\",\"nomisEventType\":\"OFFENDER_CONTACT-INSERTED\" }",
    "TopicArn": "arn:aws:sns:eu-west-1:000000000000:offender_events", 
    "MessageAttributes": {
      "eventType": {"Type": "String", "Value": "$eventType"}, 
      "id": {"Type": "String", "Value": "8b07cbd9-0820-0a0f-c32f-a9429b618e0b"}, 
      "contentType": {"Type": "String", "Value": "text/plain;charset=UTF-8"}, 
      "timestamp": {"Type": "Number.java.lang.Long", "Value": "1571666478344"}
    }
}
  """.trimIndent()

fun contactRestrictionEvent(
  eventType: String,
  restrictionId: Long,
  personId: Long,
  contactId: Long,
  offenderNo: String,
  auditModuleName: String = "OIUOVRES",
) =
  // language=JSON
  """{
    "MessageId": "ae06c49e-1f41-4b9f-b2f2-dcca610d02cd", "Type": "Notification", "Timestamp": "2019-10-21T14:01:18.500Z", 
    "Message": "{\"eventId\":\"5958295\",\"eventType\":\"$eventType\",\"eventDatetime\":\"2019-10-21T15:00:25.489964\",\"personId\": \"$personId\",\"offenderIdDisplay\": \"$offenderNo\",\"contactPersonId\": \"$contactId\",\"offenderPersonRestrictionId\": \"$restrictionId\",\"auditModuleName\":\"$auditModuleName\",\"restrictionType\": \"BAN\",\"effectiveDate\": \"2021-10-15\",\"expiryDate\": \"2022-01-13\",\"enteredById\": \"485887\",\"nomisEventType\":\"OFF_PERS_RESTRICTS-UPDATED\" }",
    "TopicArn": "arn:aws:sns:eu-west-1:000000000000:offender_events", 
    "MessageAttributes": {
      "eventType": {"Type": "String", "Value": "$eventType"}, 
      "id": {"Type": "String", "Value": "8b07cbd9-0820-0a0f-c32f-a9429b618e0b"}, 
      "contentType": {"Type": "String", "Value": "text/plain;charset=UTF-8"}, 
      "timestamp": {"Type": "Number.java.lang.Long", "Value": "1571666478344"}
    }
}
  """.trimIndent()
