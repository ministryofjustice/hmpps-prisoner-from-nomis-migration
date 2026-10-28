package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.personlocation

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrNullWhenNotFound
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.api.OffenderPersonLocationsResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.api.PersonLocationBookingResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.api.PersonLocationMovementResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderPersonLocationsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PersonLocationBooking
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PersonLocationMovement

@Service
class PersonLocationNomisApiService(@Qualifier("nomisApiWebClient") private val webClient: WebClient) {

  private val offenderApi = OffenderPersonLocationsResourceApi(webClient)
  private val bookingApi = PersonLocationBookingResourceApi(webClient)
  private val movementApi = PersonLocationMovementResourceApi(webClient)

  suspend fun getOffenderPersonLocationsOrNull(offenderNo: String): OffenderPersonLocationsResponse? = offenderApi.prepare(offenderApi.getOffenderPersonLocationsRequestConfig(offenderNo))
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun getPersonLocationBooking(offenderNo: String, bookingId: Long): PersonLocationBooking = bookingApi.getPersonLocationBooking(offenderNo, bookingId)
    .awaitSingle()

  suspend fun getPersonLocationMovement(offenderNo: String, bookingId: Long, movementSeq: Int): PersonLocationMovement = movementApi.getPersonLocationMovement(offenderNo, bookingId, movementSeq)
    .awaitSingle()
}
