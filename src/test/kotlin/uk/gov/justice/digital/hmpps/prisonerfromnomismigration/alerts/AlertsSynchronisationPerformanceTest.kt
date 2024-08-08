package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts

import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts.AlertsDpsApiExtension.Companion.dpsAlertsServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts.AlertsDpsApiMockServer.Companion.resyncedAlert
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.prisonerReceivedDomainEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.HmppsAuthApiExtension.Companion.hmppsAuth
import java.util.UUID

/**
 * This test can be thrown away if the client token get timeout logic is included in the kotlin template library
 * Setting the `api.timeout` value above the `visibilityTimeout` setting will show the bug since the token get will take too long
 */
@SpringBootTest(
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
  properties = [
    "api.timeout=2s",
    "hmpps.sqs.queues.eventalerts.visibilityTimeout=3",
    "nomis.mapping.api.client.id=fake-mapping-client-id",
    "nomis.mapping.api.secret=fake-mapping-secret",
  ],
)
class AlertsSynchronisationPerformanceTest : SqsIntegrationTestBase() {
  @Autowired
  private lateinit var alertsNomisApiMockServer: AlertsNomisApiMockServer

  @Autowired
  private lateinit var alertsMappingApiMockServer: AlertsMappingApiMockServer

  @Nested
  @DisplayName("prisoner-offender-search.prisoner.received")
  inner class PrisonerReceived {
    @Nested
    @DisplayName("auth token for mapping service initially takes too long to retrieve")
    inner class AuthTimeout {
      val offenderNo = "A3864DZ"

      @BeforeEach
      fun setUp() {
        val bookingId = 1234L
        val dpsAlertId1 = UUID.fromString("956d4326-b0c3-47ac-ab12-f0165109a6c5")
        val dpsAlertId2 = UUID.fromString("f612a10f-4827-4022-be96-d882193dfabd")

        alertsNomisApiMockServer.stubGetAlertsToResynchronise(offenderNo, bookingId = bookingId, currentAlertCount = 2)
        dpsAlertsServer.stubResynchroniseAlerts(
          offenderNo = offenderNo,
          response = listOf(
            resyncedAlert().copy(offenderBookId = bookingId, alertSeq = 1, alertUuid = dpsAlertId1),
            resyncedAlert().copy(offenderBookId = bookingId, alertSeq = 2, alertUuid = dpsAlertId2),
          ),
        )

        // stub just the mapping auth token request so it hangs initially for 4 seconds and then works promptly
        hmppsAuth.stubGrantToken(4_000, timeoutForAuthorizationHeader = "fake-mapping-client-id:fake-mapping-secret")
        hmppsAuth.setScenarioState("TokenTimeoutScenario", "Grant Timeout")

        alertsMappingApiMockServer.stubReplaceMappings(offenderNo)
        awsSqsSentencingOffenderEventsClient.sendMessage(
          alertsQueueOffenderEventsUrl,
          prisonerReceivedDomainEvent(
            offenderNo = offenderNo,
          ),
        )
        waitForAnyProcessingToComplete("alert-mapping-replace-success")
      }

      @Test
      fun `will timeout before message becomes visible again`() {
        alertsNomisApiMockServer.verify(
          count = 1,
          getRequestedFor(urlPathEqualTo("/prisoners/$offenderNo/alerts/to-migrate")),
        )
      }

      @Test
      fun `will eventually replace mapping between the DPS and NOMIS alerts`() {
        alertsMappingApiMockServer.verify(
          count = 1,
          putRequestedFor(urlPathEqualTo("/mapping/alerts/$offenderNo/all")),
        )
      }
    }
  }
}
