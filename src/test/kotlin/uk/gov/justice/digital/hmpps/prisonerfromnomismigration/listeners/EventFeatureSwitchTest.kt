package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase

@TestPropertySource(
  properties = [
    "feature.event.IEP_UPSERTED=true",
    "feature.event.OTHER_EVENT=false",
    "feature.event.casenote.GENERIC_EVENT=false",
  ],
)
internal class EventFeatureSwitchTest : SqsIntegrationTestBase() {

  @Autowired
  private lateinit var featureSwitch: EventFeatureSwitch

  @Test
  fun `should return true when feature is enabled`() {
    assertThat(featureSwitch.isEnabled("IEP_UPSERTED")).isTrue
  }

  @Test
  fun `should return false when feature is disabled`() {
    assertThat(featureSwitch.isEnabled("OTHER_EVENT")).isFalse
  }

  @Test
  fun `should return true when feature switch is not present`() {
    assertThat(featureSwitch.isEnabled("NO_SWITCH_EVENT")).isTrue
  }

  @Nested
  inner class DomainSwitch {
    @Test
    fun `should return true when feature switch is not present `() {
      assertThat(featureSwitch.isEnabled("GENERIC_EVENT")).isTrue
    }

    @Test
    fun `should return true when feature switch is not present for domain `() {
      assertThat(featureSwitch.isEnabled("GENERIC_EVENT", domain = "alert")).isTrue
    }

    @Test
    fun `should return false when feature switch is present for domain `() {
      assertThat(featureSwitch.isEnabled("GENERIC_EVENT", domain = "casenote")).isFalse()
    }
  }
}
