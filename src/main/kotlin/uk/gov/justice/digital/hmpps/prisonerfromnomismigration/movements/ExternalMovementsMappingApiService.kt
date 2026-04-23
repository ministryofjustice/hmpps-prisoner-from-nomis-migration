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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.api.TapApplicationResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.api.TapScheduleResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.api.TemporaryAbsenceResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ExternalMovementSyncMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.FindTapScheduleMappingsForAddressResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TapApplicationMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TapScheduleMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TemporaryAbsenceMoveBookingMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TemporaryAbsencesPrisonerMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TemporaryAbsencesPrisonerMappingIdsDto

@Service
class ExternalMovementsMappingApiService(@Qualifier("tapsMappingApiWebClient") webClient: WebClient) : MigrationMapping<TemporaryAbsencesPrisonerMappingDto>(domainUrl = "/mapping/temporary-absence", webClient) {

  private val mappingApi = TemporaryAbsenceResourceApi(webClient)
  private val applicationApi = TapApplicationResourceApi(webClient)
  private val scheduleApi = TapScheduleResourceApi(webClient)

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

  suspend fun createTapApplicationMapping(mapping: TapApplicationMappingDto) = applicationApi.prepare(applicationApi.createTapApplicationMappingRequestConfig(mapping))
    .retrieve()
    .awaitSuccessOrDuplicate<TapApplicationMappingDto>()

  suspend fun getTapApplicationMappingOrNull(nomisApplicationId: Long): TapApplicationMappingDto? = applicationApi.prepare(applicationApi.getTapApplicationMappingByNomisIdRequestConfig(nomisApplicationId))
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun deleteTapApplicationMapping(nomisApplicationId: Long): Unit = applicationApi.deleteTapApplicationByNomisId(nomisApplicationId).awaitSingle()

  suspend fun createTapScheduleMapping(mapping: TapScheduleMappingDto) = scheduleApi.prepare(scheduleApi.createTapScheduleMappingRequestConfig((mapping)))
    .retrieve()
    .awaitSuccessOrDuplicate<TapScheduleMappingDto>()

  suspend fun updateTapScheduleMapping(mapping: TapScheduleMappingDto) = scheduleApi.updateTapScheduleMapping(mapping)
    .awaitSingle()

  suspend fun getTapScheduleMappingOrNull(nomisEventId: Long): TapScheduleMappingDto? = scheduleApi.prepare(scheduleApi.getTapScheduleMappingByNomisIdRequestConfig(nomisEventId))
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun deleteTapScheduleMapping(nomisEventId: Long): Unit = scheduleApi.deleteTapScheduleMappingByNomisId(nomisEventId)
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

  suspend fun findTapScheduleMappingsForAddress(nomisAddressId: Long): FindTapScheduleMappingsForAddressResponse = scheduleApi.findTapScheduleMappingsByNomisAddressId(nomisAddressId)
    .awaitSingle()

  suspend fun getMoveBookingMappings(bookingId: Long): TemporaryAbsenceMoveBookingMappingDto = mappingApi.getMoveBookingMappings(bookingId).awaitSingle()

  suspend fun moveBookingMappings(bookingId: Long, fromOffenderNo: String, toOffenderNo: String): Unit = mappingApi.moveBookingMappings(bookingId, fromOffenderNo, toOffenderNo)
    .awaitSingle()

  suspend fun getPrisonerMappingIds(prisoner: String): TemporaryAbsencesPrisonerMappingIdsDto = mappingApi.getTemporaryAbsenceMappings(prisoner).awaitSingle()
}
