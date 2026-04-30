package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.court

import kotlinx.coroutines.reactive.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.api.CourtScheduleResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CourtScheduleOut

@Service
class CourtNomisApiService(@Qualifier("nomisApiWebClient") private val webClient: WebClient) {

  private val scheduleApi = CourtScheduleResourceApi(webClient)

  suspend fun getCourtScheduleOut(offenderNo: String, eventId: Long): CourtScheduleOut = scheduleApi.getCourtScheduleOut(offenderNo, eventId)
    .awaitSingle()
}
