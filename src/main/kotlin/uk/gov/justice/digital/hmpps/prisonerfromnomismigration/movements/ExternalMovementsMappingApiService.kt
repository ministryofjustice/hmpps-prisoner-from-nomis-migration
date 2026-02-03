package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements

import kotlinx.coroutines.reactive.awaitFirstOrDefault
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.awaitBody
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrNullWhenNotFound
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.CreateMappingResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.MigrationMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ExternalMovementSyncMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.FindScheduledMovementsForAddressResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ScheduledMovementSyncMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TemporaryAbsenceApplicationSyncMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TemporaryAbsencesPrisonerMappingDto

@Service
class ExternalMovementsMappingApiService(@Qualifier("extMovementsMappingApiWebClient") webClient: WebClient) : MigrationMapping<TemporaryAbsencesPrisonerMappingDto>(domainUrl = "/mapping/temporary-absence", webClient) {

  override suspend fun createMapping(
    mapping: TemporaryAbsencesPrisonerMappingDto,
    errorJavaClass: ParameterizedTypeReference<DuplicateErrorResponse<TemporaryAbsencesPrisonerMappingDto>>,
  ): CreateMappingResult<TemporaryAbsencesPrisonerMappingDto> = webClient.put()
    .uri(createMappingUrl())
    .bodyValue(mapping)
    .retrieve()
    .bodyToMono(Unit::class.java)
    .map { CreateMappingResult<TemporaryAbsencesPrisonerMappingDto>() }
    .onErrorResume(WebClientResponseException.Conflict::class.java) {
      Mono.just(CreateMappingResult(it.getResponseBodyAs(errorJavaClass)))
    }
    .awaitFirstOrDefault(CreateMappingResult())

  override fun createMappingUrl() = "$domainUrl/migrate"

  suspend fun createApplicationMapping(mapping: TemporaryAbsenceApplicationSyncMappingDto) = webClient.createMapping("$domainUrl/application", mapping)

  suspend fun getApplicationMappingOrNull(nomisApplicationId: Long): TemporaryAbsenceApplicationSyncMappingDto? = webClient.get()
    .uri("$domainUrl/application/nomis-application-id/{nomisApplicationId}", nomisApplicationId)
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun deleteApplicationMapping(nomisApplicationId: Long) = webClient.delete()
    .uri("$domainUrl/application/nomis-application-id/{nomisApplicationId}", nomisApplicationId)
    .retrieve()
    .awaitBodyOrNullWhenNotFound<Unit>()

  suspend fun createScheduledMovementMapping(mapping: ScheduledMovementSyncMappingDto) = webClient.createMapping("$domainUrl/scheduled-movement", mapping)

  suspend fun updateScheduledMovementMapping(mapping: ScheduledMovementSyncMappingDto) = webClient.updateMapping("$domainUrl/scheduled-movement", mapping)

  suspend fun getScheduledMovementMappingOrNull(nomisEventId: Long): ScheduledMovementSyncMappingDto? = webClient.get()
    .uri("$domainUrl/scheduled-movement/nomis-event-id/{nomisEventId}", nomisEventId)
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun deleteScheduledMovementMapping(nomisEventId: Long) = webClient.delete()
    .uri("$domainUrl/scheduled-movement/nomis-event-id/{nomisEventId}", nomisEventId)
    .retrieve()
    .awaitBodyOrNullWhenNotFound<Unit>()

  suspend fun createExternalMovementMapping(mapping: ExternalMovementSyncMappingDto) = webClient.createMapping("$domainUrl/external-movement", mapping)

  suspend fun updateExternalMovementMapping(mapping: ExternalMovementSyncMappingDto) = webClient.updateMapping("$domainUrl/external-movement", mapping)

  suspend fun getExternalMovementMappingOrNull(bookingId: Long, movementSeq: Int): ExternalMovementSyncMappingDto? = webClient.get()
    .uri("$domainUrl/external-movement/nomis-movement-id/{bookingId}/{movementSeq}", bookingId, movementSeq)
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun deleteExternalMovementMapping(bookingId: Long, movementSeq: Int) = webClient.delete()
    .uri("$domainUrl/external-movement/nomis-movement-id/{bookingId}/{movementSeq}", bookingId, movementSeq)
    .retrieve()
    .awaitBodyOrNullWhenNotFound<Unit>()

  suspend fun findScheduledMovementMappingsForAddress(nomisAddressId: Long): FindScheduledMovementsForAddressResponse = webClient.get()
    .uri("$domainUrl/scheduled-movements/nomis-address-id/{nomisAddressId}", nomisAddressId)
    .retrieve()
    .awaitBody()

  private suspend inline fun <reified T : Any> WebClient.createMapping(url: String, mapping: T): CreateMappingResult<T> = post()
    .uri(url)
    .bodyValue(mapping)
    .retrieve()
    .bodyToMono(Unit::class.java)
    .map { CreateMappingResult<T>() }
    .onErrorResume(WebClientResponseException.Conflict::class.java) {
      Mono.just(CreateMappingResult(it.getResponseBodyAs(object : ParameterizedTypeReference<DuplicateErrorResponse<T>>() {})))
    }
    .awaitFirstOrDefault(CreateMappingResult())

  private suspend inline fun <reified T : Any> WebClient.updateMapping(url: String, mapping: T) = put()
    .uri(url)
    .bodyValue(mapping)
    .retrieve()
    .awaitBody<Unit>()
}
