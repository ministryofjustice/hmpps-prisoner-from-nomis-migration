package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.incentives

import com.microsoft.applicationinsights.TelemetryClient
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.springframework.boot.test.mock.mockito.SpyBean
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.validIepCreatedMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi

class PrisonerFromNomisIntTest : SqsIntegrationTestBase() {

  @SpyBean
  private lateinit var telemetryClient: TelemetryClient

  @Test
  fun `will consume a prison offender events message`() {

    val message = validIepCreatedMessage()

    nomisApi.stubGetIncentive(bookingId = 1234, incentiveSequence = 1)

    awsSqsOffenderEventsClient.sendMessage(queueOffenderEventsUrl, message)

    await untilAsserted { nomisApi.verifyGetIncentive(bookingId = 1234, incentiveSequence = 1) }

    verify(telemetryClient).trackEvent(
      eq("prison-offender-event-received"),
      eq(mapOf("eventType" to "IEP_UPSERTED", "bookingId" to "1234", "incentiveSequence" to "1")),
      isNull()
    )

    // TODO - add incentives integration testing when implemented
  }

  private fun getNumberOfMessagesCurrentlyOnQueue(url: String = queueOffenderEventsUrl): Int? {
    val queueAttributes = awsSqsOffenderEventsClient.getQueueAttributes(url, listOf("ApproximateNumberOfMessages"))
    val messagesOnQueue = queueAttributes.attributes["ApproximateNumberOfMessages"]?.toInt()
    return messagesOnQueue
  }
}
