package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.ExternalMovementsNomisApiMockServer.Companion.application
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.ExternalMovementsNomisApiMockServer.Companion.temporaryAbsencesResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TemporaryAbsencesPrisonerMappingIdsDto
import java.time.LocalDate
import kotlin.collections.listOf

class ExternalMovementsMigrationTest {

  private val someMappingIds = TemporaryAbsencesPrisonerMappingIdsDto("any", listOf(), listOf(), listOf())

  @Nested
  inner class ApplicationStatus {
    @Test
    fun `should set expired if approved scheduled, inactive booking not ended yet`() {
      val nomisResponse = temporaryAbsencesResponse(
        activeBooking = false,
        applications = listOf(
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
    fun `should set expired if approved unscheduled, inactive booking not ended yet`() {
      val nomisResponse = temporaryAbsencesResponse(
        activeBooking = false,
        applications = listOf(
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
      val nomisResponse = temporaryAbsencesResponse(
        activeBooking = true,
        applications = listOf(
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
      val nomisResponse = temporaryAbsencesResponse(
        activeBooking = false,
        applications = listOf(
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
      val nomisResponse = temporaryAbsencesResponse(
        activeBooking = false,
        applications = listOf(
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
