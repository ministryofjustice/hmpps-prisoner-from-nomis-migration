package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.bedassignment

import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.test.context.TestPropertySource
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.hmpps.sqs.HmppsQueue
import uk.gov.justice.hmpps.sqs.countAllMessagesOnQueue

@TestPropertySource(
  properties = [
    "feature.event.bedassignment.BED_ASSIGNMENT_HISTORY-INSERTED=false",
    "feature.event.bedassignment.BED_ASSIGNMENT_HISTORY-UPDATED=false",
    "feature.event.bedassignment.BED_ASSIGNMENT_HISTORY-DELETED=false",
  ],
)
class BedAssignmentSyncFeatureSwitchIntTest : BedAssignmentIntegrationTestBase() {
  override fun getQueues(): List<HmppsQueue> = listOf(bedAssignmentEventQueue)

  @Nested
  inner class DisabledFeatureSwitch {
    @Test
    fun `does not fetch or sync history for any disabled bed assignment event`() {
      listOf(
        "BED_ASSIGNMENT_HISTORY-INSERTED",
        "BED_ASSIGNMENT_HISTORY-UPDATED",
        "BED_ASSIGNMENT_HISTORY-DELETED",
      ).forEach { eventType ->
        bedAssignmentEventClient.sendMessage(
          bedAssignmentEventQueueUrl,
          disabledSwitchEvent(eventType),
        )
      }

      await untilCallTo {
        bedAssignmentEventQueue.sqsClient.countAllMessagesOnQueue(bedAssignmentEventQueueUrl).get()
      } matches { it == 0 }

      nomisApi.verify(0, com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor(urlPathMatching("/prisoners/.*")))
      BedAssignmentDpsApiExtension.bedAssignmentApi.verify(
        0,
        putRequestedFor(urlPathMatching("/nomis-sync/bed-assignments/sync/.*")),
      )
    }
  }
}

private fun disabledSwitchEvent(eventType: String) = """
{
  "MessageId":"ae06c49e-1f41-4b9f-b2f2-dcca610d02cd",
  "Type":"Notification",
  "Message":"{\"eventType\":\"$eventType\",\"eventDatetime\":\"2024-07-10T15:00:25.489964\",\"auditModuleName\":\"ODSCREEN\",\"bookingId\":123456,\"offenderIdDisplay\":\"A1234KT\",\"bedAssignmentSeq\":1}",
  "MessageAttributes":{"eventType":{"Type":"String","Value":"$eventType"}}
}
""".trimIndent()
