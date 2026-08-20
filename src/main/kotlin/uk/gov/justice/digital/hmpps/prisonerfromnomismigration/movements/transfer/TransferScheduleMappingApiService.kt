package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.transfer

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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.MigrationMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.api.TransferMovementResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.api.TransferScheduleResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.api.TransferSchedulerMigrationResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.api.TransferSchedulerPrisonerResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TransferMovementMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TransferScheduleMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TransferSchedulerPrisonerMappingIdsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TransferSchedulerPrisonerMappingsDto

@Service
class TransferScheduleMappingApiService(@Qualifier("transferScheduleMappingApiWebClient") webClient: WebClient) : MigrationMapping<TransferSchedulerPrisonerMappingsDto>(domainUrl = "/mapping/transfer-scheduler", webClient) {

  private val scheduleApi = TransferScheduleResourceApi(webClient)
  private val movementApi = TransferMovementResourceApi(webClient)
  private val migrationApi = TransferSchedulerMigrationResourceApi(webClient)
  private val prisonerApi = TransferSchedulerPrisonerResourceApi(webClient)

  override suspend fun createMapping(
    mapping: TransferSchedulerPrisonerMappingsDto,
    errorJavaClass: ParameterizedTypeReference<DuplicateErrorResponse<TransferSchedulerPrisonerMappingsDto>>,
  ): CreateMappingResult<TransferSchedulerPrisonerMappingsDto> = migrationApi.prepare(migrationApi.createPrisonerTransferSchedulerMappingsRequestConfig(mapping))
    .retrieve()
    .bodyToMono<Unit>()
    .map { CreateMappingResult<TransferSchedulerPrisonerMappingsDto>() }
    .onErrorResume(WebClientResponseException.Conflict::class.java) {
      Mono.just(CreateMappingResult(it.getResponseBodyAs(errorJavaClass)))
    }
    .awaitFirstOrDefault(CreateMappingResult())

  suspend fun createTransferScheduleMapping(mapping: TransferScheduleMappingDto) = scheduleApi.prepare(scheduleApi.createTransferScheduleMappingRequestConfig(mapping))
    .retrieve()
    .awaitSuccessOrDuplicate<TransferScheduleMappingDto>()

  suspend fun getTransferScheduleMappingOrNull(nomisEventId: Long): TransferScheduleMappingDto? = scheduleApi.prepare(scheduleApi.getTransferScheduleMappingByNomisIdRequestConfig(nomisEventId))
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun deleteTransferScheduleMapping(nomisEventId: Long): Unit = scheduleApi.deleteTransferScheduleMappingByNomisId(nomisEventId)
    .awaitSingle()

  suspend fun createTransferMovementMapping(mapping: TransferMovementMappingDto) = movementApi.prepare(movementApi.createTransferMovementMappingRequestConfig(mapping))
    .retrieve()
    .awaitSuccessOrDuplicate<TransferMovementMappingDto>()

  suspend fun getTransferMovementMappingOrNull(nomisBookingId: Long, nomisMovementSeq: Int): TransferMovementMappingDto? = movementApi.prepare(movementApi.getTransferMovementMappingByNomisIdRequestConfig(nomisBookingId, nomisMovementSeq))
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun deleteTransferMovementMapping(nomisBookingId: Long, nomisMovementSeq: Int): Unit = movementApi.deleteTransferMovementMappingByNomisId(nomisBookingId, nomisMovementSeq)
    .awaitSingle()

  suspend fun getMappings(offenderNo: String): TransferSchedulerPrisonerMappingIdsDto = prisonerApi.getAllTransferSchedulerPrisonerMappingIds(offenderNo).awaitSingle()
}
