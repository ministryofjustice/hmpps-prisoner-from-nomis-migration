package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.taps

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.taps.TapNomisApiMockServer.Companion.application
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.taps.TapNomisApiMockServer.Companion.temporaryAbsencesResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.toDpsRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TemporaryAbsencesPrisonerMappingIdsDto
import java.time.LocalDate

class TapMigrationTest {

  private val someMappingIds = TemporaryAbsencesPrisonerMappingIdsDto("any", listOf(), listOf(), listOf())

  @Nested
  inner class ApplicationStatus {
    @Test
    fun `should set expired if approved scheduled, old booking not ended yet`() {
      val nomisResponse = temporaryAbsencesResponse(
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

      Assertions.assertThat(dpsRequest.temporaryAbsences.first().statusCode).isEqualTo("EXPIRED")
    }

    @Test
    fun `should set expired if approved unscheduled, old booking not ended yet`() {
      val nomisResponse = temporaryAbsencesResponse(
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

      Assertions.assertThat(dpsRequest.temporaryAbsences.first().statusCode).isEqualTo("EXPIRED")
    }

    @Test
    fun `should NOT set expired if active booking is true`() {
      val nomisResponse = temporaryAbsencesResponse(
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

      Assertions.assertThat(dpsRequest.temporaryAbsences.first().statusCode).isEqualTo("APPROVED")
    }

    @Test
    fun `should NOT set expired if application ended`() {
      val nomisResponse = temporaryAbsencesResponse(
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

      Assertions.assertThat(dpsRequest.temporaryAbsences.first().statusCode).isEqualTo("APPROVED")
    }

    @Test
    fun `should NOT set expired if application not approved`() {
      val nomisResponse = temporaryAbsencesResponse(
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

      Assertions.assertThat(dpsRequest.temporaryAbsences.first().statusCode).isEqualTo("PENDING")
    }
  }
}
