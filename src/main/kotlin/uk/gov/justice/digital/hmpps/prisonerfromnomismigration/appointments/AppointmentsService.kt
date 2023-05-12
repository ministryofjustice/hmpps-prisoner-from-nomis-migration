package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.appointments

import com.fasterxml.jackson.annotation.JsonFormat
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@Service
class AppointmentsService(@Qualifier("activitiesApiWebClient") private val webClient: WebClient) {
  suspend fun createAppointment(appointmentMigrateRequest: AppointmentMigrateRequest): CreateAppointmentResponse =
    webClient.post()
      .uri("/migrate-appointment")
      .bodyValue(appointmentMigrateRequest)
      .retrieve()
      .awaitBody()
}

data class CreateAppointmentResponse(
  val id: Long,
)

data class AppointmentMigrateRequest(

  @field:Size(max = 6, message = "Prison code should not exceed {max} characters")
  val prisonCode: String,

  @field:Size(max = 10, message = "Prisoner number should not exceed {max} characters")
  val prisonerNumber: String,

  @field:NotNull(message = "Booking ID must be supplied")
  val bookingId: Long?,

  @field:Size(max = 12, message = "Category code should not exceed {max} characters")
  val categoryCode: String,

  val internalLocationId: Long?,

  @JsonFormat(pattern = "yyyy-MM-dd")
  val startDate: LocalDate,

  @JsonFormat(pattern = "HH:mm")
  val startTime: LocalTime,

  @JsonFormat(pattern = "HH:mm")
  val endTime: LocalTime?,

  val comment: String?,
  val isCancelled: Boolean?,

  @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
  val created: LocalDateTime,

  @field:Size(max = 100, message = "Created by should not exceed {max} characters")
  val createdBy: String,

  @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
  val updated: LocalDateTime?,

  @field:Size(max = 100, message = "Updated by should not exceed {max} characters")
  val updatedBy: String?,
)
