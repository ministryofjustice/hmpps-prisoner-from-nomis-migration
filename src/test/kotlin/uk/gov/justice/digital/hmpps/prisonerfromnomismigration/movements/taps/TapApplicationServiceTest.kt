package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.taps

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.taps.TapNomisApiMockServer.Companion.temporaryAbsenceApplicationResponse
import java.time.LocalDate

class TapApplicationServiceTest {

  @Nested
  inner class ApplicationStatusExpired {
    @Test
    fun `should set expired if approved scheduled, inactive booking not ended yet`() {
      val nomisResponse = temporaryAbsenceApplicationResponse(
        activeBooking = true,
        latestBooking = false,
        status = "APP-SCH",
        fromDate = LocalDate.now().minusDays(1),
        toDate = LocalDate.now(),
      )

      val dpsRequest = nomisResponse.toDpsRequest()

      Assertions.assertThat(dpsRequest.statusCode).isEqualTo("EXPIRED")
    }

    @Test
    fun `should set expired if approved unscheduled, inactive booking not ended yet`() {
      val nomisResponse = temporaryAbsenceApplicationResponse(
        activeBooking = true,
        latestBooking = false,
        status = "APP-UNSCH",
        fromDate = LocalDate.now().minusDays(1),
        toDate = LocalDate.now(),
      )

      val dpsRequest = nomisResponse.toDpsRequest()

      Assertions.assertThat(dpsRequest.statusCode).isEqualTo("EXPIRED")
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

      Assertions.assertThat(dpsRequest.statusCode).isEqualTo("APPROVED")
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

      Assertions.assertThat(dpsRequest.statusCode).isEqualTo("PENDING")
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

      Assertions.assertThat(dpsRequest.statusCode).isEqualTo("APPROVED")
    }
  }
}
