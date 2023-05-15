package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.appointments

import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.MigrationMapping
import java.time.LocalDateTime

@Service
class AppointmentsMappingService(@Qualifier("mappingApiWebClient") webClient: WebClient) :
  MigrationMapping<AppointmentMapping>(domainUrl = "/mapping/appointments", webClient) {

  suspend fun findNomisMapping(eventId: Long): AppointmentMapping? {
    return webClient.get()
      .uri("/mapping/appointments/nomis-event-id/{eventId}", eventId)
      .retrieve()
      .bodyToMono(AppointmentMapping::class.java)
      .onErrorResume(WebClientResponseException.NotFound::class.java) {
        Mono.empty()
      }
      .awaitSingleOrNull()
  }
}

data class AppointmentMapping(
  val appointmentInstanceId: Long,
  val nomisEventId: Long,
  val mappingType: String,
  val label: String? = null,
  val whenCreated: LocalDateTime? = null,
)
