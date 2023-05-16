package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.appointments

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities.model.Appointment
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities.model.AppointmentMigrateRequest

@Service
class AppointmentsService(@Qualifier("activitiesApiWebClient") private val webClient: WebClient) {
  suspend fun createAppointment(appointmentMigrateRequest: AppointmentMigrateRequest): Appointment =
    webClient.post()
      .uri("/migrate-appointment")
      .bodyValue(appointmentMigrateRequest)
      .retrieve()
      .awaitBody()
}
