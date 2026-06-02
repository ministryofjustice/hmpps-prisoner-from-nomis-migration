package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.taps

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.taps.TapNomisApiMockServer.Companion.application
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.taps.TapNomisApiMockServer.Companion.offenderTapsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TapPrisonerMappingIdsDto
import java.time.LocalDate

class TapMigrationTest {

  private val someMappingIds = TapPrisonerMappingIdsDto("any", listOf(), listOf(), listOf())

  @Nested
  inner class ApplicationStatus {
    @Test
    fun `should set expired if approved scheduled, old booking not ended yet`() {
      val nomisResponse = offenderTapsResponse(
        activeBooking = true,
        latestBooking = false,
        tapApplications = listOf(
          application(
            fromDate = LocalDate.now().minusDays(1),
            toDate = LocalDate.now(),
            status = "APP-SCH",
          ),
        ),
      )

      val dpsRequest = nomisResponse.toDpsRequest(someMappingIds)

      assertThat(dpsRequest.temporaryAbsences.first().statusCode).isEqualTo("EXPIRED")
    }

    @Test
    fun `should set expired if approved unscheduled, old booking not ended yet`() {
      val nomisResponse = offenderTapsResponse(
        activeBooking = true,
        latestBooking = false,
        tapApplications = listOf(
          application(
            fromDate = LocalDate.now().minusDays(1),
            toDate = LocalDate.now(),
            status = "APP-UNSCH",
          ),
        ),
      )

      val dpsRequest = nomisResponse.toDpsRequest(someMappingIds)

      assertThat(dpsRequest.temporaryAbsences.first().statusCode).isEqualTo("EXPIRED")
    }

    @Test
    fun `should NOT set expired if active booking is true`() {
      val nomisResponse = offenderTapsResponse(
        activeBooking = true,
        tapApplications = listOf(
          application(
            fromDate = LocalDate.now().minusDays(1),
            toDate = LocalDate.now(),
            status = "APP-SCH",
          ),
        ),
      )

      val dpsRequest = nomisResponse.toDpsRequest(someMappingIds)

      assertThat(dpsRequest.temporaryAbsences.first().statusCode).isEqualTo("APPROVED")
    }

    @Test
    fun `should NOT set expired if application ended`() {
      val nomisResponse = offenderTapsResponse(
        activeBooking = false,
        tapApplications = listOf(
          application(
            fromDate = LocalDate.now().minusDays(2),
            toDate = LocalDate.now().minusDays(1),
            status = "APP-SCH",
          ),
        ),
      )

      val dpsRequest = nomisResponse.toDpsRequest(someMappingIds)

      assertThat(dpsRequest.temporaryAbsences.first().statusCode).isEqualTo("APPROVED")
    }

    @Test
    fun `should NOT set expired if application not approved`() {
      val nomisResponse = offenderTapsResponse(
        activeBooking = false,
        tapApplications = listOf(
          application(
            fromDate = LocalDate.now().minusDays(1),
            toDate = LocalDate.now(),
            status = "PEN",
          ),
        ),
      )

      val dpsRequest = nomisResponse.toDpsRequest(someMappingIds)

      assertThat(dpsRequest.temporaryAbsences.first().statusCode).isEqualTo("PENDING")
    }
  }
}
