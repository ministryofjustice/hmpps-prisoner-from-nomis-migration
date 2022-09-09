package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.incentives

import com.microsoft.applicationinsights.TelemetryClient
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.internal.verification.Times
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.springframework.boot.test.mock.mockito.SpyBean
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.validIepCreatedMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.IncentivesApiExtension.Companion.incentivesApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi

class PrisonerFromNomisIntTest : SqsIntegrationTestBase() {

  @SpyBean
  private lateinit var telemetryClient: TelemetryClient

  @Nested
  @DisplayName("synchronise create incentive")
  inner class SynchroniseCreate {
    @Test
    fun `will synchronise an incentive after a NOMIS incentive is created`() {

      val message = validIepCreatedMessage()

      nomisApi.stubGetIncentive(bookingId = 1234, incentiveSequence = 1)
      mappingApi.stubNomisIncentiveMappingNotFound(nomisBookingId = 1234, nomisIncentiveSequence = 1)
      mappingApi.stubIncentiveMappingCreate()
      incentivesApi.stubCreateSynchroniseIncentive()

      awsSqsOffenderEventsClient.sendMessage(queueOffenderEventsUrl, message)

      await untilAsserted { mappingApi.verifyCreateIncentiveMapping() }

      verify(telemetryClient).trackEvent(
        eq("incentive-created-synchronisation"),
        eq(
          mapOf(
            "bookingId" to "1234",
            "incentiveSequence" to "1",
            "incentiveId" to "654321",
            "auditModuleName" to "OIDOIEPS"
          )
        ),
        isNull()
      )
    }
  }

  @Nested
  @DisplayName("synchronise update incentive")
  inner class SynchroniseUpdate {
    @Test
    fun `will synchronise an incentive after a nomis update to a current incentive`() {

      val message = validIepCreatedMessage()

      nomisApi.stubGetIncentive(bookingId = 1234, incentiveSequence = 1, currentIep = true)
      mappingApi.stubIncentiveMappingByNomisIds(nomisBookingId = 1234, nomisIncentiveSequence = 1)
      incentivesApi.stubUpdateSynchroniseIncentive()
      awsSqsOffenderEventsClient.sendMessage(queueOffenderEventsUrl, message)

      await untilAsserted { incentivesApi.verifyUpdateSynchroniseIncentive(1) }

      verify(telemetryClient, Times(1)).trackEvent(
        eq("incentive-updated-synchronisation"),
        eq(
          mapOf(
            "bookingId" to "1234",
            "incentiveSequence" to "1",
            "auditModuleName" to "OIDOIEPS",
            "currentIep" to "true"
          )
        ),
        isNull()
      )
    }

    @Test
    fun `will synchronise an incentive after a nomis update to a non-current incentive`() {

      val message = validIepCreatedMessage()

      nomisApi.stubGetIncentive(bookingId = 1234, incentiveSequence = 1, currentIep = false)
      mappingApi.stubIncentiveMappingByNomisIds(nomisBookingId = 1234, nomisIncentiveSequence = 1)
      incentivesApi.stubUpdateSynchroniseIncentive()
      awsSqsOffenderEventsClient.sendMessage(queueOffenderEventsUrl, message)
      nomisApi.stubGetCurrentIncentive(bookingId = 1234, incentiveSequence = 2)

      await untilAsserted { incentivesApi.verifyUpdateSynchroniseIncentive(2) }

      verify(telemetryClient, Times(2)).trackEvent(
        eq("incentive-updated-synchronisation"),
        any(),
        isNull()
      )
    }
  }
}
