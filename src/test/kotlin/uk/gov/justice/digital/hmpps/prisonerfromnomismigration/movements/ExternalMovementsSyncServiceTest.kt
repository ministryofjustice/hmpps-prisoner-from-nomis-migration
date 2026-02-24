package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.ExternalMovementsNomisApiMockServer.Companion.scheduledTemporaryAbsenceResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.ExternalMovementsNomisApiMockServer.Companion.temporaryAbsenceApplicationResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.Location
import java.time.LocalDate

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

  @Nested
  inner class ApplicationStatusExpired {
    @Test
    fun `should set expired if approved scheduled, inactive booking not ended yet`() {
      val nomisResponse = temporaryAbsenceApplicationResponse(
        activeBooking = false,
        status = "APP-SCH",
        fromDate = LocalDate.now().minusDays(1),
        toDate = LocalDate.now(),
      )

      val dpsRequest = nomisResponse.toDpsRequest()

      assertThat(dpsRequest.statusCode).isEqualTo("EXPIRED")
    }

    @Test
    fun `should set expired if approved unscheduled, inactive booking not ended yet`() {
      val nomisResponse = temporaryAbsenceApplicationResponse(
        activeBooking = false,
        status = "APP-UNSCH",
        fromDate = LocalDate.now().minusDays(1),
        toDate = LocalDate.now(),
      )

      val dpsRequest = nomisResponse.toDpsRequest()

      assertThat(dpsRequest.statusCode).isEqualTo("EXPIRED")
    }

    @Test
    fun `should NOT set expired if active booking`() {
      val nomisResponse = temporaryAbsenceApplicationResponse(
        activeBooking = true,
        status = "APP-SCH",
        fromDate = LocalDate.now().minusDays(1),
        toDate = LocalDate.now(),
      )

      val dpsRequest = nomisResponse.toDpsRequest()

      assertThat(dpsRequest.statusCode).isEqualTo("APPROVED")
    }

    @Test
    fun `should set expired if status not approved`() {
      val nomisResponse = temporaryAbsenceApplicationResponse(
        activeBooking = false,
        status = "PEN",
        fromDate = LocalDate.now().minusDays(1),
        toDate = LocalDate.now(),
      )

      val dpsRequest = nomisResponse.toDpsRequest()

      assertThat(dpsRequest.statusCode).isEqualTo("PENDING")
    }

    @Test
    fun `should NOT set expired if application has ended`() {
      val nomisResponse = temporaryAbsenceApplicationResponse(
        activeBooking = false,
        status = "APP-SCH",
        fromDate = LocalDate.now().minusDays(2),
        toDate = LocalDate.now().minusDays(1),
      )

      val dpsRequest = nomisResponse.toDpsRequest()

      assertThat(dpsRequest.statusCode).isEqualTo("APPROVED")
    }
  }
}
