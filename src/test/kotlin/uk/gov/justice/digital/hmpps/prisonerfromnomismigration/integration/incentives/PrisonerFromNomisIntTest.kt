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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi

class PrisonerFromNomisIntTest : SqsIntegrationTestBase() {

  @SpyBean
  private lateinit var telemetryClient: TelemetryClient

  @Test
  fun `will consume a prison offender events message for a NOMIS created incentive`() {

    val message = validIepCreatedMessage()

    nomisApi.stubGetIncentive(bookingId = 1234, incentiveSequence = 1)
    mappingApi.stubNomisIncentiveMappingNotFound(nomisBookingId = 1234, nomisIncentiveSequence = 1)
    mappingApi.stubIncentiveMappingCreate()

    awsSqsOffenderEventsClient.sendMessage(queueOffenderEventsUrl, message)

    await untilAsserted { mappingApi.verifyCreateIncentiveMapping() }

    verify(telemetryClient).trackEvent(
      eq("incentive-created-synchronisation"),
      eq(mapOf("bookingId" to "1234", "incentiveSequence" to "1", "auditModuleName" to "OIDOIEPS")),
      isNull()
    )

    // TODO - add incentive api create call
  }

  @Test
  fun `will consume a prison offender events message for an NOMIS updated incentive`() {

    val message = validIepCreatedMessage()

    nomisApi.stubGetIncentive(bookingId = 1234, incentiveSequence = 1)
    mappingApi.stubIncentiveMappingByNomisIds(nomisBookingId = 1234, nomisIncentiveSequence = 1)

    awsSqsOffenderEventsClient.sendMessage(queueOffenderEventsUrl, message)

    await untilAsserted { nomisApi.verifyGetIncentive(bookingId = 1234, incentiveSequence = 1) }

    verify(telemetryClient).trackEvent(
      eq("incentive-updated-synchronisation"),
      eq(mapOf("bookingId" to "1234", "incentiveSequence" to "1", "auditModuleName" to "OIDOIEPS")),
      isNull()
    )

    // TODO - add incentive api update call
  }
}
