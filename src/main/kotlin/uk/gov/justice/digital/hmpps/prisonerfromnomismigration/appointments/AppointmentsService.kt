package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.appointments

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonInclude
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import java.time.LocalDateTime

@Service
class AppointmentsService(@Qualifier("activitiesApiWebClient") private val webClient: WebClient) {
  suspend fun createAppointment(createAppointmentRequest: CreateAppointmentRequest): CreateAppointmentResponse =
    webClient.post()
      .uri("/appointments/TODO")
      .bodyValue(createAppointmentRequest)
      .retrieve()
      .awaitBody()
}

@JsonInclude(JsonInclude.Include.NON_NULL)
data class CreateAppointmentRequest(
  // TODO
  val bookingId: Long,
  val offenderNo: String,
  val prisonId: String? = null,
  val internalLocation: Long? = null,
  @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
  val startDateTime: LocalDateTime? = null,
  @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
  val endDateTime: LocalDateTime? = null,
  val comment: String? = null,
  val subtype: String,
  val status: String,
)

data class CreateAppointmentResponse(
  val appointmentInstanceId: Long,
)
