package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.mergeDomainEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage

class CorePersonSynchronisationIntTest : CorePersonIntegrationTestBase() {
  @Nested
  @DisplayName("prison-offender-events.prisoner.merged")
  inner class PrisonerMerge {
    @Nested
    inner class HappyPath {
      val bookingId = 1234567L
      val offenderNo = "A1234KT"
      val removedOffenderNo = "A1000KT"

      @BeforeEach
      fun setUp() {
        awsSqsCorePersonOffenderEventsClient.sendMessage(
          corePersonQueueOffenderEventsUrl,
          mergeDomainEvent(
            bookingId = bookingId,
            offenderNo = offenderNo,
            removedOffenderNo = removedOffenderNo,
          ),
        )
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will track telemetry for the merge`() {
        verify(telemetryClient).trackEvent(
          eq("coreperson-prisoner-merge-synchronisation-notimplemented"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(offenderNo)
            assertThat(it["bookingId"]).isEqualTo(bookingId.toString())
            assertThat(it["removedOffenderNo"]).isEqualTo(removedOffenderNo)
          },
          isNull(),
        )
      }
    }
  }
}
