package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visits

import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.internal.verification.Times
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.validVisitCancellationMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.VisitsApiExtension.Companion.visitsApi

class VisitsSynchronisationIntTest : SqsIntegrationTestBase() {

  @Nested
  @DisplayName("synchronise cancel visit")
  inner class SynchroniseCancelVisit {
    private val vsipId = "6c3ce237-f519"

    @Test
    fun `will synchronise a visit cancellation after a nomis visit cancellation event`() {
      val message = validVisitCancellationMessage()

      nomisApi.stubGetCancelledVisit(nomisVisitId = 9, modifyUserId = "user1")
      mappingApi.stubVisitMappingByNomisVisitId(nomisVisitId = 9, vsipId = vsipId)
      visitsApi.stubCancelVisit(vsipId)
      awsSqsVisitsOffenderEventsClient.sendMessage(visitsQueueOffenderEventsUrl, message)

      await untilAsserted {
        verify(telemetryClient, Times(1)).trackEvent(
          eq("visit-cancellation-synchronisation"),
          eq(
            mapOf(
              "offenderNo" to "A7948DY",
              "vsipId" to vsipId,
              "vsipOutcome" to "PRISONER_CANCELLED",
              "nomisVisitId" to "9",
            ),
          ),
          isNull(),
        )
      }

      visitsApi.verify(
        putRequestedFor(urlEqualTo("/migrate-visits/$vsipId/cancel"))
          .withRequestBody(matchingJsonPath("actionedBy", equalTo("user1")))
          .withRequestBody(matchingJsonPath("cancelOutcome.outcomeStatus", equalTo("PRISONER_CANCELLED")))
          .withRequestBody(matchingJsonPath("cancelOutcome.text", equalTo("Cancelled by NOMIS"))),
      )
      visitsApi.verifyCancelVisit(times = 1) // this call checks that the verify check itself is correct
    }

    @Test
    fun `will ignore a visit cancellation event without a mapping`() {
      val message = validVisitCancellationMessage()

      nomisApi.stubGetVisit(nomisVisitId = 9)
      mappingApi.stubAllMappingsNotFound(MappingApiExtension.VISITS_GET_MAPPING_URL)
      awsSqsVisitsOffenderEventsClient.sendMessage(visitsQueueOffenderEventsUrl, message)

      await untilAsserted { mappingApi.verifyGetVisitMappingByNomisId() }

      visitsApi.verifyCancelVisit(times = 0)
    }

    @Test
    fun `will ignore a visit cancellation event that originated in VSIP`() {
      val message = validVisitCancellationMessage()

      nomisApi.stubGetCancelledVisit(nomisVisitId = 9, modifyUserId = "PRISONER_MANAGER_API")
      mappingApi.stubAllMappingsNotFound(MappingApiExtension.VISITS_GET_MAPPING_URL)
      awsSqsVisitsOffenderEventsClient.sendMessage(visitsQueueOffenderEventsUrl, message)

      await untilAsserted { nomisApi.verifyGetVisit(nomisVisitId = 9) }

      mappingApi.verifyGetVisitMappingByNomisId(times = 0)
    }
  }
}
