package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.transfer

import kotlinx.coroutines.reactive.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.api.TransferMovementResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.api.TransferScheduleResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.TransferMovementOut
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.TransferScheduleOut

@Service
class TransferScheduleNomisApiService(@Qualifier("nomisApiWebClient") private val webClient: WebClient) {

  private val transferScheduleApi = TransferScheduleResourceApi(webClient)
  private val transferMovementApi = TransferMovementResourceApi(webClient)

  suspend fun getTransferScheduleOut(offenderNo: String, eventId: Long): TransferScheduleOut = transferScheduleApi.getTransferScheduleOut(offenderNo, eventId)
    .awaitSingle()

  suspend fun getTransferMovementOut(offenderNo: String, bookingId: Long, sequence: Int): TransferMovementOut = transferMovementApi.getTransferMovementOut(offenderNo, bookingId, sequence)
    .awaitSingle()
}
