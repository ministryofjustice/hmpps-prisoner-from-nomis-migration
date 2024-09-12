package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip

import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase

class CSIPAttendeeSynchronisationIntTest : SqsIntegrationTestBase() {
  @Autowired
  private lateinit var csipNomisApi: CSIPNomisApiMockServer

  @Autowired
  private lateinit var csipMappingApi: CSIPMappingApiMockServer
}

fun csipAttendeeEvent(
  eventType: String = "CSIP_ATTENDEES-INSERTED",
  csipAttendeeId: String,
  csipReportId: String = "1234",
  auditModuleName: String = "OIDCSIPC",
) = """
  {
    "Type" : "Notification",
    "MessageId" : "7bdec840-69e5-5163-8013-967eb63d3d26",
    "TopicArn" : "arn:aws:sns:eu-west-2:754256621582:cloud-platform-Digital-Prison-Services-f221e27fcfcf78f6ab4f4c3cc165eee7",
    "Message" : "{\"eventType\":\"$eventType\",\"eventDatetime\":\"2024-06-11T10:39:17\",\"bookingId\":1215724,\"offenderIdDisplay\":\"A1234BC\",\"nomisEventType\":\"$eventType\",\"rootOffenderId\":2581911,\"csipAttendeeId\":\"$csipAttendeeId\",\"csipReportId\":\"$csipReportId\",\"auditModuleName\":\"$auditModuleName\"}",
    "Timestamp" : "2024-02-08T13:56:40.981Z",
    "SignatureVersion" : "1",
    "Signature" : "ZUU+9m0kLuVMVE0KCwk5LN1bhQQ6VTOP7djMUaJFYB/+s8kKpAh4Hm5XbIrqbAIoDJmf2MF+GxGRe1sypAn7z61GqqotcXI6r5CjiCvQVsrcwQqO0qoUkb5NoXWyBCG4MOaasFYfjleDnthQS/+rnNWT9Ndl09QtAhjfztHnD279GbrVhywj9O1xcDpnIkx/zGsZUbQsPZDOTOcfeV0M8mbrJhWMWefg9fZ05LeLljD4B8DjMfkmMAn3nBszWlZQcQPDReV7xoMPA+dXJpYXXx6PRLPRtfs7BFGA1hsuYI0mXZb3V3QBvG4Jt5IEYPkfKGZDEmf/hK9V7WkfBiDu2A==",
    "SigningCertURL" : "https://sns.eu-west-2.amazonaws.com/SimpleNotificationService-60eadc530605d63b8e62a523676ef735.pem",
    "UnsubscribeURL" : "https://sns.eu-west-2.amazonaws.com/?Action=Unsubscribe&SubscriptionArn=arn:aws:sns:eu-west-2:754256621582:cloud-platform-Digital-Prison-Services-f221e27fcfcf78f6ab4f4c3cc165eee7:902e0982-6d9a-4430-9aae-e055362dc824",
    "MessageAttributes" : {
      "publishedAt" : {"Type":"String","Value":"2024-02-08T13:56:40.96292265Z"},
      "eventType" : {"Type":"String","Value":"$eventType"}
    }
} 
""".trimIndent()
