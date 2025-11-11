package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.ExternalMovementsNomisApiMockServer.Companion.scheduledTemporaryAbsenceResponse
import java.time.LocalDateTime

class ExternalMovementsSyncServiceTest {
  private val now = LocalDateTime.now()
  private val yesterday = now.minusDays(1)
  private val tomorrow = now.plusDays(1)

  @Nested
  @DisplayName("Transform scheduled movement to DPS Occurrence")
  inner class TransformToSyncWriteTapOccurrence {

    @Test
    fun `should default escort code if null`() {
      val nomis = scheduledTemporaryAbsenceResponse().copy(
        escort = null,
      )

      with(nomis.toDpsRequest()) {
        assertThat(accompaniedByCode).isEqualTo("U")
      }
    }

    @Test
    fun `should default transport type code if null`() {
      val nomis = scheduledTemporaryAbsenceResponse().copy(
        transportType = null,
      )

      with(nomis.toDpsRequest()) {
        assertThat(transportCode).isEqualTo("TNR")
      }
    }

    @Test
    fun `should set status to OVERDUE if no inbound schedule and beyond return time`() {
      val nomis = scheduledTemporaryAbsenceResponse().copy(
        inboundEventStatus = null,
        returnTime = yesterday,
      )

      with(nomis.toDpsRequest()) {
        assertThat(statusCode).isEqualTo("OVERDUE")
      }
    }

    @Test
    fun `should set status to OVERDUE if inbound schedule not complete and beyond return time`() {
      val nomis = scheduledTemporaryAbsenceResponse().copy(
        inboundEventStatus = "SCH",
        returnTime = yesterday,
      )

      with(nomis.toDpsRequest()) {
        assertThat(statusCode).isEqualTo("OVERDUE")
      }
    }

    @Test
    fun `should set status to IN_PROGRESS if no inbound schedule but NOT beyond return time`() {
      val nomis = scheduledTemporaryAbsenceResponse().copy(
        inboundEventStatus = null,
        returnTime = tomorrow,
      )

      with(nomis.toDpsRequest()) {
        assertThat(statusCode).isEqualTo("IN_PROGRESS")
      }
    }

    @Test
    fun `should set status to IN_PROGRESS if inbound schedule not complete and NOT beyond return time`() {
      val nomis = scheduledTemporaryAbsenceResponse().copy(
        inboundEventStatus = "SCH",
        returnTime = tomorrow,
      )

      with(nomis.toDpsRequest()) {
        assertThat(statusCode).isEqualTo("IN_PROGRESS")
      }
    }

    @Test
    fun `should set status to COMPLETED if inbound schedule complete`() {
      val nomis = scheduledTemporaryAbsenceResponse().copy(
        inboundEventStatus = "COMP",
      )

      with(nomis.toDpsRequest()) {
        assertThat(statusCode).isEqualTo("COMPLETED")
      }
    }

    @Test
    fun `should set status to COMPLETED if inbound schedule complete and NOT beyond return time`() {
      val nomis = scheduledTemporaryAbsenceResponse().copy(
        inboundEventStatus = "COMP",
        returnTime = tomorrow,
      )

      with(nomis.toDpsRequest()) {
        assertThat(statusCode).isEqualTo("COMPLETED")
      }
    }

    @ParameterizedTest
    @CsvSource(
      value = [
        "CANC,CANCELLED",
        "DEN,DENIED",
        "EXP,EXPIRED",
        "PEN,PENDING",
        "SCH,SCHEDULED",
      ],
    )
    fun `should use outbound schedule status regardless of inbound status or return time`(nomisOutStatus: String, dpsStatus: String) {
      val inboundStatuses = listOf("CANC", "DEN", "EXP", "PEN", "SCH", null)
      val returnTimes = listOf(yesterday, tomorrow)

      inboundStatuses.forEach { inboundStatus ->
        returnTimes.forEach { returnTime ->
          val nomis = scheduledTemporaryAbsenceResponse().copy(
            eventStatus = nomisOutStatus,
            inboundEventStatus = inboundStatus,
            returnTime = returnTime,
          )

          with(nomis.toDpsRequest()) {
            assertThat(statusCode).isEqualTo(dpsStatus)
          }
        }
      }
    }
  }
}
