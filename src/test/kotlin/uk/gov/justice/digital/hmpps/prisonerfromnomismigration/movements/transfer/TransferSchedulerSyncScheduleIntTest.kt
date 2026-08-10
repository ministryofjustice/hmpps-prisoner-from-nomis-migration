package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.transfer

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.transfer.TransferScheduleDpsApiExtension.Companion.dpsTransferSchedulerServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisSyncApiExtension

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TransferSchedulerSyncScheduleIntTest : TransferSchedulerIntegrationTestBase() {

  private val dpsApi = dpsTransferSchedulerServer

  override fun resetTelemetryClient() {}

  private fun setUpTestClass() {
    NomisApiExtension.resetAndDisableResetBeforeEach()
    NomisSyncApiExtension.resetAndDisableResetBeforeEach()
    MappingApiExtension.resetAndDisableResetBeforeEach()
    TransferScheduleDpsApiExtension.resetAndDisableResetBeforeEach()

    reset(telemetryClient)
  }

  @Nested
  @DisplayName("SCHEDULED_EXT_MOVE-INSERTED")
  inner class TransferScheduleCreated {

    @Nested
    inner class HappyPath {
      @BeforeEach
      fun setUp() {
        setUpTestClass()

        sendMessage(transferScheduleEvent("SCHEDULED_EXT_MOVE-INSERTED"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should raise telemetry`() = runTest {
        verify(telemetryClient).trackEvent(
          eq("transfer-scheduler-sync-schedule-inserted-success"),
          any(),
          isNull(),
        )
      }
    }
  }

  private fun sendMessage(event: String) = awsSqsTransferMovementsOffenderEventsClient.sendMessage(
    transferMovementsQueueOffenderEventsUrl,
    event,
  )

  private fun transferScheduleEvent(eventType: String, auditModuleName: String = "OCUCANTR", nomisEventType: String = "TRN", direction: String = "OUT", eventId: Long = 45678) = // language=JSON
    """{
         "Type" : "Notification",
         "MessageId" : "57126174-e2d7-518f-914e-0056a63363b0",
         "TopicArn" : "arn:aws:sns:eu-west-2:754256621582:cloud-platform-Digital-Prison-Services-f221e27fcfcf78f6ab4f4c3cc165eee7",
         "Message" : "{\"eventType\":\"$eventType\",\"eventDatetime\":\"2025-09-02T09:19:03\",\"nomisEventType\":\"$eventType\",\"bookingId\":12345,\"offenderIdDisplay\":\"A1234BC\",\"eventId\":$eventId,\"eventMovementType\":\"$nomisEventType\",\"auditModuleName\":\"$auditModuleName\",\"directionCode\":\"OUT\"}",
         "Timestamp" : "2025-09-02T09:19:03.998Z",
         "SignatureVersion" : "1",
         "Signature" : "eePe/HtUdMyeFriH6GJe4FAJjYhQFjohJOu0+t8qULvpaw+qsGBfolKYa83fARpGDZJf9ceKd6kYGwF+OVeNViXluqPeUyoWbJ/lOjCs1tvlUuceCLy/7+eGGxkNASKJ1sWdwhO5J5I8WKUq5vfyYgL/Mygae6U71Bc0H9I2uVkw7tUYg0ZQBMSkA8HpuLLAN06qR5ahJnNDDxxoV07KY6E2dy8TheEo2Dhxq8hicl272LxWKMifM9VfR+D1i1eZNXDGsvvHmMCjumpxxYAJmrU+aqUzAU2KnhoZJTfeZT+RV+ZazjPLqX52zwA47ZFcqzCBnmrU6XwuHT4gKJcj1Q==",
         "SigningCertURL" : "https://sns.eu-west-2.amazonaws.com/SimpleNotificationService-6209c161c6221fdf56ec1eb5c821d112.pem",
         "UnsubscribeURL" : "https://sns.eu-west-2.amazonaws.com/?Action=Unsubscribe&SubscriptionArn=arn:aws:sns:eu-west-2:754256621582:cloud-platform-Digital-Prison-Services-f221e27fcfcf78f6ab4f4c3cc165eee7:3b68e1dd-c229-490f-bff9-05bd53595ddc",
         "MessageAttributes" : {
           "publishedAt" : {"Type":"String","Value":"2025-09-02T09:19:03.976312166+01:00"},
           "traceparent" : {"Type":"String","Value":"00-a0103c496069d331bd417cac78f4085c-0158c9f6485e8841-01"},
           "eventType" : {"Type":"String","Value":"$eventType"}
         }
       }
    """.trimMargin()
}
