package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.calculatereleasedates

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage
import uk.gov.justice.hmpps.sqs.HmppsQueue

class CalculateReleaseDatesSynchronisationIntTest : CalculateReleaseDatesIntegrationTestBase() {
  override fun getQueues(): List<HmppsQueue> = listOf(calculateReleaseDatesEventQueue)

  @Nested
  inner class SentenceCalculationEvents {
    @ParameterizedTest
    @CsvSource(
      "OFFENDER_SENT_CALCULATIONS-INSERTED,calculatereleasedates-synchronisation-sentence-calculation-created",
      "OFFENDER_SENT_CALCULATIONS-UPDATED,calculatereleasedates-synchronisation-sentence-calculation-updated",
      "OFFENDER_SENT_CALCULATIONS-DELETED,calculatereleasedates-synchronisation-sentence-calculation-deleted",
    )
    fun `will track telemetry for each event type`(eventType: String, telemetryName: String) {
      calculateReleaseDatesEventQueue.sendMessage(calculateReleaseDatesEventMessage(eventType))
        .also { waitForAnyProcessingToComplete() }

      verify(telemetryClient).trackEvent(
        eq(telemetryName),
        check {
          assertThat(it["offenderNo"]).isEqualTo("A1234KT")
          assertThat(it["bookingId"]).isEqualTo("123456")
          assertThat(it["nomisCalculationId"]).isEqualTo("98765")
        },
        isNull(),
      )
    }
  }
}
