package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrNullWhenNotFound
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.MigrationMapping

@Service
class ExternalMovementsMappingApiService(@Qualifier("mappingApiWebClient") webClient: WebClient) : MigrationMapping<TemporaryAbsencesPrisonerMappingDto>(domainUrl = "/mapping/temporary-absences", webClient) {
  suspend fun getPrisonerTemporaryAbsenceMappings(prisonerNumber: String): TemporaryAbsencesPrisonerMappingDto? = webClient.get()
    .uri("$domainUrl/nomis-prisoner-number/{prisonerNumber}", prisonerNumber)
    .retrieve()
    .awaitBodyOrNullWhenNotFound()
}

// TODO SDIT-2873 This is a placeholder - replace with generated DTO when available
data class TemporaryAbsencesPrisonerMappingDto(
  val prisonerNumber: String,
  val bookings: List<TemporaryAbsenceBookingMappingDto>,
  val migrationId: String,
  val whenCreated: String? = null,
)

data class TemporaryAbsenceBookingMappingDto(
  val bookingId: Long,
  val applications: List<TemporaryAbsenceApplicationMappingDto>,
  val unscheduledMovements: List<ExternalMovementMappingDto>,
)

data class TemporaryAbsenceApplicationMappingDto(
  val nomisMovementApplicationId: Long,
  val dpsMovementApplicationId: Long,
  val outsideMovements: List<TemporaryAbsencesOutsideMovementMappingDto>,
  val schedules: List<ScheduledMovementMappingDto>,
  val movements: List<ExternalMovementMappingDto>,
)

data class TemporaryAbsencesOutsideMovementMappingDto(
  val nomisMovementApplicationMultiId: Long,
  val dpsOutsideMovementId: Long,
)

data class ScheduledMovementMappingDto(
  val nomisEventId: Long,
  val dpsScheduledMovementId: Long,
)

data class ExternalMovementMappingDto(
  val nomisMovementSeq: Long,
  val dpsExternalMovementId: Long,
)
