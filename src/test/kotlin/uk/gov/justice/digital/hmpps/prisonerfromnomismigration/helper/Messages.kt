package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper

fun validVisitCancellationMessage() = """{
    "MessageId": "ae06c49e-1f41-4b9f-b2f2-dcca610d02cd", "Type": "Notification", "Timestamp": "2019-10-21T14:01:18.500Z", 
    "Message": "{\"eventId\":\"5958295\",\"eventType\":\"VISIT_CANCELLED\",\"eventDatetime\":\"2019-10-21T15:00:25.489964\",\"offenderIdDisplay\":\"G4803UT\",\"agencyLocationId\":\"MDI\",\"bookingId\": \"1234\",\"nomisEventType\":\"OFFENDER_VISIT-UPDATED\",\"visitId\":\"9\",\"auditModuleName\":\"OIDOVIS\" }",
    "TopicArn": "arn:aws:sns:eu-west-1:000000000000:offender_events", 
    "MessageAttributes": {
      "eventType": {"Type": "String", "Value": "VISIT_CANCELLED"}, 
      "id": {"Type": "String", "Value": "8b07cbd9-0820-0a0f-c32f-a9429b618e0b"}, 
      "contentType": {"Type": "String", "Value": "text/plain;charset=UTF-8"}, 
      "timestamp": {"Type": "Number.java.lang.Long", "Value": "1571666478344"}
    }
}
""".trimIndent()
