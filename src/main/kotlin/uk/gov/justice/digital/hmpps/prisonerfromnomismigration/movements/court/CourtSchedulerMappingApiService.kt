package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.court

import kotlinx.coroutines.reactive.awaitFirstOrDefault
import kotlinx.coroutines.reactive.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrNullWhenNotFound
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitSuccessOrDuplicate
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.CreateMappingResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.api.CourtMovementResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.api.CourtScheduleResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.api.CourtSchedulerMigrationResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.api.CourtSchedulerPrisonerResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtMovementMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtScheduleMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtSchedulerPrisonerMappingsDto

@Service
class CourtSchedulerMappingApiService(@Qualifier("courtSchedulerMappingApiWebClient") webClient: WebClient) {

  private val scheduleApi = CourtScheduleResourceApi(webClient)
  private val movementApi = CourtMovementResourceApi(webClient)
  private val migrationApi = CourtSchedulerMigrationResourceApi(webClient)
  private val prisonerApi = CourtSchedulerPrisonerResourceApi(webClient)

  suspend fun createMapping(
    mapping: CourtSchedulerPrisonerMappingsDto,
    errorJavaClass: ParameterizedTypeReference<DuplicateErrorResponse<CourtSchedulerPrisonerMappingsDto>>,
  ): CreateMappingResult<CourtSchedulerPrisonerMappingsDto> = migrationApi.prepare(migrationApi.createPrisonerCourtSchedulerMappingsRequestConfig(mapping))
    .retrieve()
    .bodyToMono<Unit>()
    .map { CreateMappingResult<CourtSchedulerPrisonerMappingsDto>() }
    .onErrorResume(WebClientResponseException.Conflict::class.java) {
      Mono.just(CreateMappingResult(it.getResponseBodyAs(errorJavaClass)))
    }
    .awaitFirstOrDefault(CreateMappingResult())

  suspend fun createCourtScheduleMapping(mapping: CourtScheduleMappingDto) = scheduleApi.prepare(scheduleApi.createCourtScheduleMappingRequestConfig((mapping)))
    .retrieve()
    .awaitSuccessOrDuplicate<CourtScheduleMappingDto>()

  suspend fun getCourtScheduleMappingOrNull(nomisEventId: Long): CourtScheduleMappingDto? = scheduleApi.prepare(scheduleApi.getCourtScheduleMappingByNomisIdRequestConfig(nomisEventId))
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun deleteCourtScheduleMapping(nomisEventId: Long): Unit = scheduleApi.deleteCourtScheduleMappingByNomisId(nomisEventId)
    .awaitSingle()

  suspend fun createCourtMovementMapping(mapping: CourtMovementMappingDto) = movementApi.prepare(movementApi.createCourtMovementMappingRequestConfig(mapping))
    .retrieve()
    .awaitSuccessOrDuplicate<CourtMovementMappingDto>()

  suspend fun getCourtMovementMappingOrNull(nomisBookingId: Long, nomisMovementSeq: Int): CourtMovementMappingDto? = movementApi.prepare(movementApi.getCourtMovementMappingByNomisIdRequestConfig(nomisBookingId, nomisMovementSeq))
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun deleteCourtMovementMapping(nomisBookingId: Long, nomisMovementSeq: Int): Unit = movementApi.deleteCourtMovementMappingByNomisId(nomisBookingId, nomisMovementSeq)
    .awaitSingle()

  suspend fun getCourtSchedulerPrisonMappingIds(offenderNo: String) = prisonerApi.getAllCourtSchedulerPrisonerMappingIds(offenderNo).awaitSingle()
}
