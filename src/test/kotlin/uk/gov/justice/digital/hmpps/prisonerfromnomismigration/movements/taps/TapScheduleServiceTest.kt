package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.taps

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.Location
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.taps.TapNomisApiMockServer.Companion.scheduledTemporaryAbsenceResponse

class TapScheduleServiceTest {

  @Nested
  @DisplayName("Schedule defautls")
  inner class ScheduleDefaults {

    @Test
    fun `should default escort code if null`() {
      val nomis = scheduledTemporaryAbsenceResponse().copy(
        escort = null,
      )

      with(nomis.toDpsRequest(dpsLocation = Location(address = "any"))) {
        Assertions.assertThat(accompaniedByCode).isEqualTo("NOT_PROVIDED")
      }
    }

    @Test
    fun `should default transport type code if null`() {
      val nomis = scheduledTemporaryAbsenceResponse().copy(
        transportType = null,
      )

      with(nomis.toDpsRequest(dpsLocation = Location(address = "any"))) {
        Assertions.assertThat(transportCode).isEqualTo("TNR")
      }
    }
  }

  @Test
  fun `should send cancelled flag`() {
    val nomis = scheduledTemporaryAbsenceResponse().copy(
      eventStatus = "CANC",
    )

    with(nomis.toDpsRequest(dpsLocation = Location(address = "any"))) {
      Assertions.assertThat(isCancelled).isTrue
    }
  }

  @Nested
  @DisplayName("Schedule cancelled")
  inner class ScheduleCancelled {

    @Test
    fun `should send cancelled flag`() {
      val nomis = scheduledTemporaryAbsenceResponse().copy(
        eventStatus = "CANC",
      )

      with(nomis.toDpsRequest(dpsLocation = Location(address = "any"))) {
        Assertions.assertThat(isCancelled).isTrue
      }
    }
  }
}
