package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.personlocation

import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.verifyBlocking
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage

class PersonLocationSyncMovementIntTest : PersonLocationIntegrationTestBase() {

  @Test
  @DisplayName("EXTERNAL_MOVEMENT-CHANGED")
  fun `should call the sync service when an external movement changes`() {
    personLocationsOffenderEventsQueue.sendMessage(externalMovementEvent())

    await untilAsserted { verifyBlocking(personLocationSyncMovementService) { externalMovementChanged(any()) } }
  }

  private fun externalMovementEvent(prisonerNumber: String = "A1234BC") = // language=JSON
    """{
         "Type" : "Notification",
         "MessageId" : "83354f3f-45cb-5e8e-9266-2e0fa1e91dcc",
         "TopicArn" : "arn:aws:sns:eu-west-2:754256621582:cloud-platform-Digital-Prison-Services-160f3055cc4e04c4105ee85f2ed1fccb",
         "Message" : "{\"eventType\":\"EXTERNAL_MOVEMENT-CHANGED\",\"eventDatetime\":\"2025-09-02T13:24:01\",\"bookingId\":12345,\"offenderIdDisplay\":\"$prisonerNumber\",\"nomisEventType\":\"EXTERNAL_MOVEMENT-CHANGED\",\"movementSeq\":3,\"movementDateTime\":\"2025-09-02T13:23:00\",\"movementType\":\"TRN\",\"movementReasonCode\":\"OPA\",\"directionCode\":\"OUT\",\"fromAgencyLocationId\":\"NWI\",\"recordInserted\":true,\"recordDeleted\":false,\"auditModuleName\":\"OCUCANTR\"}",
         "Timestamp" : "2025-09-02T12:24:02.004Z",
         "MessageAttributes" : {
           "eventType" : {"Type":"String","Value":"EXTERNAL_MOVEMENT-CHANGED"}
         }
        }
    """.trimMargin()
}
