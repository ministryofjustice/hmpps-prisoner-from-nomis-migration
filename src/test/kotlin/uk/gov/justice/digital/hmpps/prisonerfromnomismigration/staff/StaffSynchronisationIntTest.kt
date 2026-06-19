package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.staff

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito.eq
import org.mockito.kotlin.check
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage

class StaffSynchronisationIntTest : StaffIntegrationTestBase() {
  val nomisStaffId = 1234L

  @Autowired
  private lateinit var nomisApiMock: StaffNomisApiMockServer

  @Autowired
  private lateinit var mappingApiMock: StaffMappingApiMockServer

  @Nested
  @DisplayName("STAFF-INSERTED")
  inner class StaffCreated {
    @Nested
    inner class WhenCreatedInDps {
      @BeforeEach
      fun setUp() {
        staffOffenderEventsQueue.sendMessage(
          staffEvent(
            eventType = "STAFF-INSERTED",
            staffId = nomisStaffId,
            auditModuleName = "DPS_SYNCHRONISATION",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("staff-synchronisation-created-notimplemented"),
          check {
            assertThat(it["nomisStaffId"]).isEqualTo(nomisStaffId.toString())
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenCreatedInNomis {

      @BeforeEach
      fun setUp() {
        staffOffenderEventsQueue.sendMessage(
          staffEvent(
            eventType = "STAFF-INSERTED",
            staffId = nomisStaffId,
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Nested
      inner class HappyPath {

        @Test
        fun `will track telemetry`() {
          verify(telemetryClient).trackEvent(
            eq("staff-synchronisation-created-notimplemented"),
            check {
              assertThat(it["nomisStaffId"]).isEqualTo(nomisStaffId.toString())
            },
            isNull(),
          )
        }
      }
    }
  }
}

fun staffEvent(
  eventType: String,
  staffId: Long,
  auditModuleName: String = "OUUUSERS",
) = // language=JSON
  """{
    "MessageId": "ae06c49e-1f41-4b9f-b2f2-dcca610d02cd", "Type": "Notification", "Timestamp": "2019-10-21T14:01:18.500Z", 
    "Message": "{\"eventType\":\"$eventType\",\"eventDatetime\":\"2019-10-21T15:00:25.489964\",\"staffId\": $staffId,\"auditModuleName\":\"$auditModuleName\",\"nomisEventType\":\"$eventType\" }",
    "TopicArn": "arn:aws:sns:eu-west-1:000000000000:offender_events", 
    "MessageAttributes": {
      "eventType": {"Type": "String", "Value": "$eventType"}, 
      "id": {"Type": "String", "Value": "8b07cbd9-0820-0a0f-c32f-a9429b618e0b"}, 
      "contentType": {"Type": "String", "Value": "text/plain;charset=UTF-8"}, 
      "timestamp": {"Type": "Number.java.lang.Long", "Value": "1571666478344"}
    }
}
  """.trimIndent()
