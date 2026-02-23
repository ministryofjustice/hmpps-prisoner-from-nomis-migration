package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements

import kotlinx.coroutines.reactive.awaitFirstOrDefault
import kotlinx.coroutines.reactive.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrNullWhenNotFound
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitSuccessOrDuplicate
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.CreateMappingResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.MigrationMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.api.TemporaryAbsenceResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ExternalMovementSyncMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.FindScheduledMovementsForAddressResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ScheduledMovementSyncMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TemporaryAbsenceApplicationSyncMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TemporaryAbsenceMoveBookingMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TemporaryAbsencesPrisonerMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TemporaryAbsencesPrisonerMappingIdsDto

@Service
class ExternalMovementsMappingApiService(@Qualifier("extMovementsMappingApiWebClient") webClient: WebClient) : MigrationMapping<TemporaryAbsencesPrisonerMappingDto>(domainUrl = "/mapping/temporary-absence", webClient) {

  private val mappingApi = TemporaryAbsenceResourceApi(webClient)

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

  suspend fun createApplicationMapping(mapping: TemporaryAbsenceApplicationSyncMappingDto) = mappingApi.prepare(mappingApi.createApplicationSyncMappingRequestConfig(mapping))
    .retrieve()
    .awaitSuccessOrDuplicate<TemporaryAbsenceApplicationSyncMappingDto>()

  suspend fun getApplicationMappingOrNull(nomisApplicationId: Long): TemporaryAbsenceApplicationSyncMappingDto? = mappingApi.prepare(mappingApi.getApplicationSyncMappingByNomisIdRequestConfig(nomisApplicationId))
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun deleteApplicationMapping(nomisApplicationId: Long): Unit = mappingApi.deleteApplicationSyncMappingByNomisId(nomisApplicationId).awaitSingle()

  suspend fun createScheduledMovementMapping(mapping: ScheduledMovementSyncMappingDto) = mappingApi.prepare(mappingApi.createScheduledMovementSyncMappingRequestConfig(mapping))
    .retrieve()
    .awaitSuccessOrDuplicate<ScheduledMovementSyncMappingDto>()

  suspend fun updateScheduledMovementMapping(mapping: ScheduledMovementSyncMappingDto) = mappingApi.updateScheduledMovementSyncMapping(mapping)
    .awaitSingle()

  suspend fun getScheduledMovementMappingOrNull(nomisEventId: Long): ScheduledMovementSyncMappingDto? = mappingApi.prepare(mappingApi.getScheduledMovementSyncMappingByNomisIdRequestConfig(nomisEventId))
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun deleteScheduledMovementMapping(nomisEventId: Long): Unit = mappingApi.deleteScheduledMovementSyncMappingByNomisId(nomisEventId)
    .awaitSingle()

  suspend fun createExternalMovementMapping(mapping: ExternalMovementSyncMappingDto) = mappingApi.prepare(mappingApi.createExternalMovementSyncMappingRequestConfig(mapping))
    .retrieve()
    .awaitSuccessOrDuplicate<ExternalMovementSyncMappingDto>()

  suspend fun updateExternalMovementMapping(mapping: ExternalMovementSyncMappingDto) = mappingApi.updateExternalMovementSyncMapping(mapping)
    .awaitSingle()

  suspend fun getExternalMovementMappingOrNull(bookingId: Long, movementSeq: Int): ExternalMovementSyncMappingDto? = mappingApi.prepare(mappingApi.getExternalMovementSyncMappingByNomisIdRequestConfig(bookingId, movementSeq))
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun deleteExternalMovementMapping(bookingId: Long, movementSeq: Int): Unit = mappingApi.deleteExternalMovementSyncMappingByNomisId(bookingId, movementSeq)
    .awaitSingle()

  suspend fun findScheduledMovementMappingsForAddress(nomisAddressId: Long): FindScheduledMovementsForAddressResponse = mappingApi.findScheduledMovementsByNomisAddressId(nomisAddressId)
    .awaitSingle()

  suspend fun getMoveBookingMappings(bookingId: Long): TemporaryAbsenceMoveBookingMappingDto = mappingApi.getMoveBookingMappings(bookingId).awaitSingle()

  suspend fun moveBookingMappings(bookingId: Long, fromOffenderNo: String, toOffenderNo: String): Unit = mappingApi.moveBookingMappings(bookingId, fromOffenderNo, toOffenderNo)
    .awaitSingle()

  suspend fun getPrisonerMappingIds(prisoner: String): TemporaryAbsencesPrisonerMappingIdsDto = mappingApi.getMappings1(prisoner).awaitSingle()
}
