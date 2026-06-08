package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.court

import kotlinx.coroutines.reactive.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrNullWhenNotFound
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.api.CourtMovementResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.api.CourtScheduleResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.api.OffenderCourtMovementsResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.BookingCourtMovements
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CourtMovementIn
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CourtMovementOut
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CourtScheduleOut
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderCourtMovementsResponse

@Service
class CourtSchedulerNomisApiService(@Qualifier("nomisApiWebClient") private val webClient: WebClient) {

  private val scheduleApi = CourtScheduleResourceApi(webClient)
  private val movementApi = CourtMovementResourceApi(webClient)
  private val offenderApi = OffenderCourtMovementsResourceApi(webClient)

  suspend fun getCourtScheduleOut(offenderNo: String, eventId: Long): CourtScheduleOut = scheduleApi.getCourtScheduleOut(offenderNo, eventId)
    .awaitSingle()

  suspend fun getCourtMovementOut(offenderNo: String, bookingId: Long, movementSequence: Int): CourtMovementOut = movementApi.getCourtMovementOut(offenderNo, bookingId, movementSequence)
    .awaitSingle()

  suspend fun getCourtMovementIn(offenderNo: String, bookingId: Long, movementSequence: Int): CourtMovementIn = movementApi.getCourtMovementIn(offenderNo, bookingId, movementSequence)
    .awaitSingle()

  suspend fun getOffenderCourtMovementsOrNull(offenderNo: String): OffenderCourtMovementsResponse? = offenderApi.prepare(offenderApi.getOffenderCourtMovementsRequestConfig(offenderNo))
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun getBookingCourtMovementsOrNull(bookingId: Long): BookingCourtMovements? = offenderApi.prepare(offenderApi.getAllBookingCourtMovementsRequestConfig(bookingId)).retrieve().awaitBodyOrNullWhenNotFound()
}
