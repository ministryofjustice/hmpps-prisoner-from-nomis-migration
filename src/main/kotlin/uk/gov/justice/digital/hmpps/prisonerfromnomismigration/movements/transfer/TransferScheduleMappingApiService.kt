package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.transfer

import kotlinx.coroutines.reactive.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrNullWhenNotFound
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitSuccessOrDuplicate
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.api.TransferMovementResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.api.TransferScheduleResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.api.TransferSchedulerMigrationResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.api.TransferSchedulerPrisonerResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TransferMovementMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TransferScheduleMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TransferSchedulerPrisonerMappingIdsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TransferSchedulerPrisonerMappingsDto

@Service
class TransferScheduleMappingApiService(@Qualifier("transferScheduleMappingApiWebClient") webClient: WebClient) {

  private val scheduleApi = TransferScheduleResourceApi(webClient)
  private val movementApi = TransferMovementResourceApi(webClient)
  private val migrationApi = TransferSchedulerMigrationResourceApi(webClient)
  private val prisonerApi = TransferSchedulerPrisonerResourceApi(webClient)

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

  suspend fun createMappings(request: TransferSchedulerPrisonerMappingsDto): Unit = migrationApi.createPrisonerTransferSchedulerMappings(request)
    .awaitSingle()

  suspend fun getMappings(offenderNo: String): TransferSchedulerPrisonerMappingIdsDto = prisonerApi.getAllTransferSchedulerPrisonerMappingIds(offenderNo).awaitSingle()
}
