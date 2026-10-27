package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.personlocation

import kotlinx.coroutines.reactive.awaitFirstOrDefault
import kotlinx.coroutines.reactor.awaitSingle
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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.MigrationMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.api.PersonLocationBookingResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.api.PersonLocationMigrationResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.api.PersonLocationMovementResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonLocationBookingIdMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonLocationBookingMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonLocationMovementIdMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonLocationsMappingDto

@Service
class PersonLocationMappingApiService(@Qualifier("personLocationMappingApiWebClient") webClient: WebClient) : MigrationMapping<PersonLocationsMappingDto>(domainUrl = "/mapping/person-location", webClient) {

  private val migrationApi = PersonLocationMigrationResourceApi(webClient)
  private val bookingApi = PersonLocationBookingResourceApi(webClient)
  private val movementApi = PersonLocationMovementResourceApi(webClient)

  suspend fun getMappings(bookingIds: List<Long>): List<PersonLocationBookingMappingDto> = migrationApi.getPersonLocationMappingsByBookingIds(bookingIds).awaitSingle()

  override suspend fun createMapping(
    mapping: PersonLocationsMappingDto,
    errorJavaClass: ParameterizedTypeReference<DuplicateErrorResponse<PersonLocationsMappingDto>>,
  ): CreateMappingResult<PersonLocationsMappingDto> = migrationApi.prepare(migrationApi.createPersonLocationMappingsRequestConfig(mapping))
    .retrieve()
    .bodyToMono<Unit>()
    .map { CreateMappingResult<PersonLocationsMappingDto>() }
    .onErrorResume(WebClientResponseException.Conflict::class.java) {
      Mono.just(CreateMappingResult(it.getResponseBodyAs(errorJavaClass)))
    }
    .awaitFirstOrDefault(CreateMappingResult())

  suspend fun createBookingMapping(mapping: PersonLocationBookingIdMappingDto) = bookingApi.prepare(bookingApi.createBookingMappingRequestConfig(mapping))
    .retrieve()
    .awaitSuccessOrDuplicate<PersonLocationBookingIdMappingDto>()

  suspend fun createMovementMapping(mapping: PersonLocationMovementIdMappingDto) = movementApi.prepare(movementApi.createMovementMappingRequestConfig(mapping))
    .retrieve()
    .awaitSuccessOrDuplicate<PersonLocationMovementIdMappingDto>()

  suspend fun getBookingMappingOrNull(nomisBookingId: Long): PersonLocationBookingIdMappingDto? = bookingApi.prepare(bookingApi.getBookingMappingByNomisIdRequestConfig(nomisBookingId))
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun getMovementMappingOrNull(nomisBookingId: Long, nomisMovementSeq: Int): PersonLocationMovementIdMappingDto? = movementApi.prepare(movementApi.getMovementMappingByNomisIdRequestConfig(nomisBookingId, nomisMovementSeq))
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun deleteBookingMapping(nomisBookingId: Long): Unit = bookingApi.deleteBookingMappingByNomisId(nomisBookingId)
    .awaitSingle()

  suspend fun deleteMovementMapping(nomisBookingId: Long, nomisMovementSeq: Int): Unit = movementApi.deleteMovementMappingByNomisId(nomisBookingId, nomisMovementSeq)
    .awaitSingle()
}
