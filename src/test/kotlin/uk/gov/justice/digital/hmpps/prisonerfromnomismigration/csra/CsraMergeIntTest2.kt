package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csra

import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.eq
import org.mockito.kotlin.check
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.mergeDomainEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage
import java.time.OffsetDateTime

class CsraMergeIntTest2(
  @Autowired private val csraMappingApiMockServer: CsraMappingApiMockServer,
  @Autowired private val csraNomisApiMockServer: CsraNomisApiMockServer,
) : CsraIntegrationTestBase() {
  private val survivorOffenderNo = "A1234BB"
  private val removedOffenderNo = "A1234AA"

  @Nested
  inner class OffenderMerged {
    @BeforeEach
    fun setUp() {
      csraMappingApiMockServer.stubUpdateMappingsByNomisId()
    }

    private fun sendMergeMessage() {
      awsSqsCsraEventClient.sendMessage(
        csraEventQueueUrl,
        mergeDomainEvent(
          offenderNo = survivorOffenderNo,
          removedOffenderNo = removedOffenderNo,
          bookingId = 10L,
          occurredAt = OffsetDateTime.now().toString(),
        ),
      )
    }

    @Test
    fun `will correct the mappings between the existing DPS and NOMIS CSRAs`() {
      sendMergeMessage()

      // ensure the process has finished before the test ends by checking the telemetry
      await untilAsserted {
        verify(telemetryClient).trackEvent(
          eq("csras-synchronisation-prisoner-merged-success"),
          check {
            assertThat(it["nomsNumber"]).isEqualTo("A1234BB")
            assertThat(it["removedNomsNumber"]).isEqualTo("A1234AA")
            assertThat(it["bookingId"]).isEqualTo("10")
          },
          isNull(),
        )
      }
      csraMappingApiMockServer.verify(
        putRequestedFor(urlPathEqualTo("/mapping/csras/merge/from/A1234AA/to/A1234BB")),
      )
    }
  }
}
