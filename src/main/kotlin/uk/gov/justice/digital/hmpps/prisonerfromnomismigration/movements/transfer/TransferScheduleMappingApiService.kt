package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.transfer

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrNullWhenNotFound
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitSuccessOrDuplicate
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.api.TransferScheduleResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TransferScheduleMappingDto

@Service
class TransferScheduleMappingApiService(@Qualifier("transferScheduleMappingApiWebClient") webClient: WebClient) {

  private val scheduleApi = TransferScheduleResourceApi(webClient)

  suspend fun createTransferScheduleMapping(mapping: TransferScheduleMappingDto) = scheduleApi.prepare(scheduleApi.createTransferScheduleMappingRequestConfig(mapping))
    .retrieve()
    .awaitSuccessOrDuplicate<TransferScheduleMappingDto>()

  suspend fun getTransferScheduleMappingOrNull(nomisEventId: Long): TransferScheduleMappingDto? = scheduleApi.prepare(scheduleApi.getTransferScheduleMappingByNomisIdRequestConfig(nomisEventId))
    .retrieve()
    .awaitBodyOrNullWhenNotFound()
}
