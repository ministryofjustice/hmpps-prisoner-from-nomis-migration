package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.ExternalMovementsNomisApiMockServer.Companion.scheduledTemporaryAbsenceResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.Location

class ExternalMovementsSyncServiceTest {

  @Nested
  @DisplayName("Transform scheduled movement to DPS Occurrence")
  inner class TransformToSyncWriteTapOccurrence {

    @Test
    fun `should default escort code if null`() {
      val nomis = scheduledTemporaryAbsenceResponse().copy(
        escort = null,
      )

      with(nomis.toDpsRequest(dpsLocation = Location(address = "any"))) {
        assertThat(accompaniedByCode).isEqualTo("NOT_PROVIDED")
      }
    }

    @Test
    fun `should default transport type code if null`() {
      val nomis = scheduledTemporaryAbsenceResponse().copy(
        transportType = null,
      )

      with(nomis.toDpsRequest(dpsLocation = Location(address = "any"))) {
        assertThat(transportCode).isEqualTo("TNR")
      }
    }
  }

  @Test
  fun `should send cancelled flag`() {
    val nomis = scheduledTemporaryAbsenceResponse().copy(
      eventStatus = "CANC",
    )

    with(nomis.toDpsRequest(dpsLocation = Location(address = "any"))) {
      assertThat(isCancelled).isTrue
    }
  }
}
