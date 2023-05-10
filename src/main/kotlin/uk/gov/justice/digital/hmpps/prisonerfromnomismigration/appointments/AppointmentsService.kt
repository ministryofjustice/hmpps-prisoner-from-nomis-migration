package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.appointments

import com.fasterxml.jackson.annotation.JsonFormat
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import java.time.LocalDate
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

  @field:NotEmpty(message = "Prison code must be supplied")
  @field:Size(max = 6, message = "Prison code should not exceed {max} characters")
  val prisonCode: String?,

  @field:NotEmpty(message = "Prisoner number must be supplied")
  @field:Size(max = 10, message = "Prisoner number should not exceed {max} characters")
  val prisonerNumber: String?,

  @field:NotNull(message = "Booking ID must be supplied")
  val bookingId: Long?,

  @field:NotEmpty(message = "Category code must be supplied")
  @field:Size(max = 12, message = "Category code should not exceed {max} characters")
  val categoryCode: String?,

  val internalLocationId: Long?,

  @field:NotNull(message = "Start date must be supplied")
  @JsonFormat(pattern = "yyyy-MM-dd")
  val startDate: LocalDate?,

  @field:NotNull(message = "Start time must be supplied")
  @JsonFormat(pattern = "HH:mm")
  val startTime: LocalTime?,

  @JsonFormat(pattern = "HH:mm")
  val endTime: LocalTime?,

  val comment: String = "",
)
