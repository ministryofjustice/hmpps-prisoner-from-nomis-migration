package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.taps

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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.api.TapMovementResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.api.TapPrisonerResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.api.TapScheduleResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.FindTapScheduleMappingsForAddressResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TapApplicationMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TapMoveBookingMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TapMovementMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TapPrisonerMappingIdsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TapPrisonerMappingsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TapScheduleMappingDto

@Service
class TapMappingApiService(@Qualifier("tapsMappingApiWebClient") webClient: WebClient) : MigrationMapping<TapPrisonerMappingsDto>(domainUrl = "/mapping/taps", webClient) {

  private val applicationApi = TapApplicationResourceApi(webClient)
  private val scheduleApi = TapScheduleResourceApi(webClient)
  private val movementApi = TapMovementResourceApi(webClient)
  private val prisonerApi = TapPrisonerResourceApi(webClient)

  override suspend fun createMapping(
    mapping: TapPrisonerMappingsDto,
    errorJavaClass: ParameterizedTypeReference<DuplicateErrorResponse<TapPrisonerMappingsDto>>,
  ): CreateMappingResult<TapPrisonerMappingsDto> = webClient.put()
    .uri(createMappingUrl())
    .bodyValue(mapping)
    .retrieve()
    .bodyToMono(Unit::class.java)
    .map { CreateMappingResult<TapPrisonerMappingsDto>() }
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

  suspend fun createTapMovementMapping(mapping: TapMovementMappingDto) = movementApi.prepare(movementApi.createTapMovementMappingRequestConfig(mapping))
    .retrieve()
    .awaitSuccessOrDuplicate<TapMovementMappingDto>()

  suspend fun updateTapMovementMapping(mapping: TapMovementMappingDto) = movementApi.updateTapMovementMapping(mapping)
    .awaitSingle()

  suspend fun getTapMovementMappingOrNull(bookingId: Long, movementSeq: Int): TapMovementMappingDto? = movementApi.prepare(movementApi.getTapMovementMappingByNomisIdRequestConfig(bookingId, movementSeq))
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun deleteTapMovementMapping(bookingId: Long, movementSeq: Int): Unit = movementApi.deleteTapMovementMappingByNomisId(bookingId, movementSeq)
    .awaitSingle()

  suspend fun findTapScheduleMappingsForAddress(nomisAddressId: Long): FindTapScheduleMappingsForAddressResponse = scheduleApi.findTapScheduleMappingsByNomisAddressId(nomisAddressId)
    .awaitSingle()

  suspend fun getTapMoveBookingMappings(bookingId: Long): TapMoveBookingMappingDto = prisonerApi.getPrisonerBookingMappings(bookingId).awaitSingle()

  suspend fun moveTapBookingMappings(bookingId: Long, fromOffenderNo: String, toOffenderNo: String): Unit = prisonerApi.movePrisonerBookingMappings(bookingId, fromOffenderNo, toOffenderNo)
    .awaitSingle()

  suspend fun getTapPrisonerMappingIds(prisoner: String): TapPrisonerMappingIdsDto = prisonerApi.getAllPrisonerMappingIds(prisoner).awaitSingle()
}
