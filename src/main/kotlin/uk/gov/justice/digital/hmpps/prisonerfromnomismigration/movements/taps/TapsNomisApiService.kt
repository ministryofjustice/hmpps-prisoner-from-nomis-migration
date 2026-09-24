package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.taps

import kotlinx.coroutines.reactive.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrNullWhenNotFound
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.api.OffenderTapsResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.api.TapMovementResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderTapsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.TapMovementIn
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.TapMovementOut

@Service
class TapsNomisApiService(@Qualifier("nomisApiWebClient") private val webClient: WebClient) {

  private val movementApi = TapMovementResourceApi(webClient)
  private val offenderApi = OffenderTapsResourceApi(webClient)

  suspend fun getAllOffenderTapsOrNull(offenderNo: String): OffenderTapsResponse? = offenderApi.prepare(offenderApi.getAllOffenderTapsRequestConfig(offenderNo))
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun getTapMovementOut(offenderNo: String, bookingId: Long, movementSeq: Int): TapMovementOut = movementApi.getTapMovementOut(offenderNo, bookingId, movementSeq)
    .awaitSingle()

  suspend fun getTapMovementIn(offenderNo: String, bookingId: Long, movementSeq: Int): TapMovementIn = movementApi.getTapMovementIn(offenderNo, bookingId, movementSeq)
    .awaitSingle()
}
